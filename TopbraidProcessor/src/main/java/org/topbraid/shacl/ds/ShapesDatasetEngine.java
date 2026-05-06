/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 */
package org.topbraid.shacl.ds;

import java.io.InputStream;
import java.util.HashMap;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.topbraid.jenax.util.JenaDatatypes;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.shacl.arq.SHACLFunctions;
import org.topbraid.shacl.ds.TargetGraphSelector.CombinationPair;
import org.topbraid.shacl.ds.TargetGraphSelector.TargetGraphPair;
import org.topbraid.shacl.ds.combinations.GraphCombination;
import org.topbraid.shacl.ds.vocabulary.SHDS;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.engine.ShapesGraphFactory;
import org.topbraid.shacl.util.SHACLUtil;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;
import org.topbraid.shacl.validation.ValidationEngineFactory;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

/**
 * Validates an RDF data dataset against a SHACL-DS shapes dataset.
 *
 * <p>A <em>shapes dataset</em> is a TriG document where each named graph is an
 * independent shapes graph. Each shapes graph declares which data graphs it targets
 * via {@code shds:targetGraph}, {@code shds:targetGraphPattern}, or
 * {@code shds:targetGraphCombination}.
 *
 * <p>The engine:
 * <ol>
 *   <li>Resolves (shapes graph, data graph) pairs via {@link TargetGraphSelector}.</li>
 *   <li>For each pair, constructs a SHACL-DS dataset view via
 *       {@link DatasetViewTransformer} (focus graph → default; original default →
 *       {@code shds:default} named graph).</li>
 *   <li>Runs TopBraid's standard {@link ValidationEngine} on that view.</li>
 *   <li>Annotates every {@code sh:ValidationResult} with {@code shds:sourceShapesGraph}
 *       and {@code shds:focusGraph}.</li>
 *   <li>Merges all per-graph reports into a single {@code sh:ValidationReport}.</li>
 * </ol>
 *
 * <p>The primary entry point is {@link ValidationUtil#validateDataset}, which
 * delegates to this class.
 *
 * @author Davan Chiem Dao
 * @see ValidationUtil#validateDataset(Dataset, Dataset)
 */
public class ShapesDatasetEngine {

    private static Dataset shaclDsDataset;

    private static synchronized Dataset getShaclDsDataset() {
        if (shaclDsDataset == null) {
            shaclDsDataset = DatasetFactory.create();
            InputStream trig = ShapesDatasetEngine.class.getResourceAsStream("/rdf/shacl-ds.trig");
            RDFDataMgr.read(shaclDsDataset, trig, Lang.TRIG);
            shaclDsDataset.listNames().forEachRemaining(name ->
                SHACLFunctions.registerFunctions(shaclDsDataset.getNamedModel(name)));
        }
        return shaclDsDataset;
    }

    /**
     * Validates {@code dataDataset} against all shapes graphs declared in
     * {@code shapesDataset}, with SHACL-DS-specific options.
     *
     * <p>If {@link DatasetValidationEngineConfiguration#isValidateShapesDataset()} is {@code true},
     * the shapes dataset is first validated against the SHACL-DS meta-shapes
     * ({@code shacl-ds.trig}). If it is non-conforming, the shapes validation report is
     * returned immediately and no data validation is run.
     *
     * @param shapesDataset the shapes dataset (each named graph is a shapes graph)
     * @param dataDataset   the data dataset to validate
     * @param configuration SHACL-DS configuration
     * @return a merged {@code sh:ValidationReport} in its own model
     */
    public static Resource validate(Dataset shapesDataset, Dataset dataDataset,
            DatasetValidationEngineConfiguration configuration) {

        if (configuration.isValidateShapesDataset()) {
            Resource shapesReport = validate(getShaclDsDataset(), shapesDataset,
                new DatasetValidationEngineConfiguration());
            if (shapesReport.hasProperty(SH.conforms, JenaDatatypes.FALSE)) {
                return shapesReport;
            }
        }

        Model reportModel = ModelFactory.createDefaultModel();
        Resource mergedReport = reportModel.createResource();
        mergedReport.addProperty(RDF.type, SH.ValidationReport);
        mergedReport.addLiteral(SH.conforms, true);

        Map<String, ShapesGraph> shapesGraphs = new HashMap<>();

        List<TargetGraphPair> pairs = TargetGraphSelector.resolveTargetGraphs(shapesDataset, dataDataset);
        List<CombinationPair> combPairs = TargetGraphSelector.resolveGraphCombinations(shapesDataset, dataDataset);

        for (TargetGraphPair pair : pairs) perpareShapesGraph(pair.shapesGraphURI(), shapesDataset, shapesGraphs);
        for (CombinationPair cp : combPairs) perpareShapesGraph(cp.shapesGraphURI(), shapesDataset, shapesGraphs);

        // --- Named graph targets ---
        for (TargetGraphPair pair : pairs) {
            Dataset view = DatasetViewTransformer.createView(dataDataset, pair.targetGraphURI());
            Resource focusGraphRes = pair.targetGraphURI() != null
                ? reportModel.createResource(pair.targetGraphURI())
                : reportModel.createResource(SHDS.DEFAULT.getURI());
            Resource partialReport = runEngine(view,
                shapesGraphs.get(pair.shapesGraphURI()),
                configuration);
            mergeResults(partialReport, mergedReport, reportModel, pair.shapesGraphURI(), focusGraphRes);
        }

        // --- Graph combination targets ---
        for (CombinationPair cp : combPairs) {
            GraphCombination combination = cp.combination();
            Model combinedModel = combination.toModel(dataDataset);
            Dataset view = DatasetViewTransformer.createCombinationView(dataDataset, combinedModel);
            Resource focusGraphRes = copyResourceDeep(combination.getNode(), reportModel);
            Resource partialReport = runEngine(view,
                shapesGraphs.get(cp.shapesGraphURI()),
                configuration);
            mergeResults(partialReport, mergedReport, reportModel, cp.shapesGraphURI(), focusGraphRes);
        }

        return mergedReport;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    // Loads and registers a shapes graph by URI into the cache if not already present.
    private static void perpareShapesGraph(String shapesGraphURI, Dataset shapesDataset,
            Map<String, ShapesGraph> shapesGraphs) {
        if (shapesGraphs.containsKey(shapesGraphURI)) return;
        Model shapesModel = ValidationUtil.ensureToshTriplesExist(
            shapesDataset.getNamedModel(shapesGraphURI));
        SHACLFunctions.registerFunctions(shapesModel);
        shapesGraphs.put(shapesGraphURI, ShapesGraphFactory.get().createShapesGraph(shapesModel));
    }


    /**
     * Prepares and runs TopBraid's {@link ValidationEngine} against a fully constructed
     * view dataset. The shapes model is added as a named graph so the engine can
     * locate it via its URI.
     */
    private static Resource runEngine(Dataset viewDataset, ShapesGraph shapesGraph,
            ValidationEngineConfiguration configuration) {

        URI shapesGraphNamedURI = SHACLUtil.createRandomShapesGraphURI();
        viewDataset.addNamedModel(shapesGraphNamedURI.toString(), shapesGraph.getShapesModel());

        ValidationEngine engine = ValidationEngineFactory.get()
            .create(viewDataset, shapesGraphNamedURI, shapesGraph, null);
        engine.setConfiguration(configuration);

        try {
            engine.applyEntailments();
            return engine.validateAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("SHACL-DS validation interrupted", e);
        }
    }

    /**
     * Copies all {@code sh:result} resources from {@code partialReport} into
     * {@code mergedReport}, annotating each with {@code shds:sourceShapesGraph}
     * and {@code shds:focusGraph}. Updates {@code sh:conforms} to {@code false}
     * if any result is added.
     *
     * <p>Each result is deep-copied as a fresh blank node to prevent blank node
     * collisions across sub-reports.
     */
    private static void mergeResults(Resource partialReport, Resource mergedReport,
            Model reportModel, String shapesGraphURI, Resource focusGraphRes) {

        reportModel.add(partialReport.getModel()); 
        reportModel.removeAll(partialReport.inModel(reportModel), SH.result, null);

        Resource shapesGraphRes = reportModel.createResource(shapesGraphURI);
        boolean hasResults = false;

        StmtIterator results = partialReport.listProperties(SH.result);
        while (results.hasNext()) {
            RDFNode resultNode = results.next().getObject();
            if (!resultNode.isResource()) continue;
            hasResults = true;

            Resource mergedResult = resultNode.asResource().inModel(reportModel);
            mergedResult.addProperty(SHDS.sourceShapesGraph, shapesGraphRes);
            mergedResult.addProperty(SHDS.focusGraph, focusGraphRes);

            mergedReport.addProperty(SH.result, mergedResult);

        }
        if (hasResults) {
            mergedReport.removeAll(SH.conforms);
            mergedReport.addLiteral(SH.conforms, false);
        }
    }

    /**
     * Deep-copies {@code source} into {@code targetModel}.
     * Named resources keep their URI; blank nodes become fresh blank nodes.
     */
    private static Resource copyResourceDeep(Resource source, Model targetModel) {
        Resource copy = source.isAnon()
            ? targetModel.createResource()
            : targetModel.createResource(source.getURI());
        copyPropertiesDeep(source, copy, targetModel);
        return copy;
    }

    /**
     * Recursively copies all properties of {@code source} onto {@code target},
     * creating fresh blank nodes in {@code targetModel} for blank node objects.
     */
    private static void copyPropertiesDeep(Resource source, Resource target, Model targetModel) {
        StmtIterator it = source.listProperties();
        while (it.hasNext()) {
            Statement stmt = it.next();
            RDFNode obj = stmt.getObject();
            if (obj.isAnon()) {
                Resource newBlank = targetModel.createResource();
                copyPropertiesDeep(obj.asResource(), newBlank, targetModel);
                target.addProperty(stmt.getPredicate(), newBlank);
            } else {
                target.addProperty(stmt.getPredicate(), obj.inModel(targetModel));
            }
        }
    }
}
