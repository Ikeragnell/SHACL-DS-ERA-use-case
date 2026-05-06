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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.topbraid.shacl.ds.combinations.GraphCombination;
import org.topbraid.shacl.ds.vocabulary.SHDS;

/**
 * Resolves SHACL-DS target graph declarations from a shapes dataset against a data dataset.
 *
 * <p>Target declarations can appear in two places in the shapes dataset:
 * <ul>
 *   <li><b>Centralised</b>: triples in the <em>default graph</em> of the shapes dataset,
 *       where the subject is the shapes graph URI.</li>
 *   <li><b>Decentralised</b>: triples inside the named shapes graph itself,
 *       where the subject is the shapes graph URI.</li>
 * </ul>
 * Both patterns are supported and their results are combined.
 *
 * @author Davan Chiem Dao
 */
public class TargetGraphSelector {

    /**
     * A resolved (shapes graph URI, target data graph URI) pair.
     * A {@code null} targetGraphURI represents the default graph.
     */
    public record TargetGraphPair(String shapesGraphURI, String targetGraphURI) {}

    /**
     * A resolved (shapes graph URI, graph combination) pair.
     */
    public record CombinationPair(String shapesGraphURI, GraphCombination combination) {}


    /**
     * Resolves all {@code shds:targetGraph} and {@code shds:targetGraphPattern}
     * declarations, applying exclusions, and returns one pair per
     * (shapes graph, data graph) to validate.
     *
     * @param shapesDataset the shapes dataset
     * @param dataDataset   the data dataset
     * @return list of target graph pairs; order is stable (declaration order preserved)
     */
    public static List<TargetGraphPair> resolveTargetGraphs(Dataset shapesDataset, Dataset dataDataset) {
        List<TargetGraphPair> results = new ArrayList<>();
        Model defaultGraph = shapesDataset.getDefaultModel();

        Iterator<String> shapesGraphNames = shapesDataset.listNames();
        while (shapesGraphNames.hasNext()) {
            String shapesGraphURI = shapesGraphNames.next();
            Model namedGraph = shapesDataset.getNamedModel(shapesGraphURI);

            // Collect included graphs from both centralised and decentralised declarations
            Set<String> included = new LinkedHashSet<>();
            for (Model scope : List.of(defaultGraph, namedGraph)) {
                Resource subj = scope.createResource(shapesGraphURI);
                collectExpanded(scope, subj, SHDS.targetGraph, dataDataset, included);
                collectByPattern(scope, subj, SHDS.targetGraphPattern, dataDataset, included);
            }
            if (included.isEmpty()) continue;

            // Collect excluded graphs
            Set<String> excluded = new LinkedHashSet<>();
            for (Model scope : List.of(defaultGraph, namedGraph)) {
                Resource subj = scope.createResource(shapesGraphURI);
                collectExpanded(scope, subj, SHDS.targetGraphExclude, dataDataset, excluded);
                collectByPattern(scope, subj, SHDS.targetGraphExcludePattern, dataDataset, excluded);
            }

            included.removeAll(excluded);
            for (String graphURI : included) {
                results.add(new TargetGraphPair(shapesGraphURI, graphURI));
            }
        }

        return results;
    }

    /**
     * Resolves all {@code shds:targetGraphCombination} declarations, scanning both
     * the default graph (centralised) and each named shapes graph (decentralised).
     *
     * @param shapesDataset the shapes dataset
     * @param dataDataset   the data dataset (used to parse combination operands)
     * @return list of combination pairs
     */
    public static List<CombinationPair> resolveGraphCombinations(Dataset shapesDataset, Dataset dataDataset) {
        List<CombinationPair> results = new ArrayList<>();
        Model defaultGraph = shapesDataset.getDefaultModel();

        Iterator<String> shapesGraphNames = shapesDataset.listNames();
        while (shapesGraphNames.hasNext()) {
            String shapesGraphURI = shapesGraphNames.next();
            Model namedGraph = shapesDataset.getNamedModel(shapesGraphURI);

            for (Model scope : List.of(defaultGraph, namedGraph)) {
                Resource subj = scope.createResource(shapesGraphURI);
                StmtIterator it = scope.listStatements(subj, SHDS.targetGraphCombination, (RDFNode) null);
                while (it.hasNext()) {
                    Statement stmt = it.next();
                    if (!stmt.getObject().isResource()) continue;
                    GraphCombination combination = GraphCombination.parse(stmt.getObject(), scope);
                    results.add(new CombinationPair(shapesGraphURI, combination));
                }
            }
        }

        return results;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void collectExpanded(Model scope, Resource subj,
            org.apache.jena.rdf.model.Property property,
            Dataset dataDataset, Set<String> target) {
        StmtIterator it = scope.listStatements(subj, property, (RDFNode) null);
        while (it.hasNext()) {
            RDFNode obj = it.next().getObject();
            if (!obj.isURIResource()) continue;
            target.addAll(expandSelector(obj.asResource().getURI(), dataDataset));
        }
    }

    private static void collectByPattern(Model scope, Resource subj,
            org.apache.jena.rdf.model.Property property,
            Dataset dataDataset, Set<String> target) {
        StmtIterator it = scope.listStatements(subj, property, (RDFNode) null);
        while (it.hasNext()) {
            Statement stmt = it.next();
            if (!stmt.getObject().isLiteral()) continue;
            Pattern pattern = Pattern.compile(stmt.getString());
            Iterator<String> names = dataDataset.listNames();
            while (names.hasNext()) {
                String name = names.next();
                if (pattern.matcher(name).matches()) target.add(name);
            }
        }
    }

    /**
     * Expands a selector URI to the concrete graph URI strings it denotes.
     * A {@code null} entry in the result represents the default graph.
     */
    private static List<String> expandSelector(String uri, Dataset dataDataset) {
        List<String> result = new ArrayList<>();
        if (SHDS.all.getURI().equals(uri)) {
            result.add(null); // default graph
            Iterator<String> names = dataDataset.listNames();
            while (names.hasNext()) result.add(names.next());
        } else if (SHDS.named.getURI().equals(uri)) {
            Iterator<String> names = dataDataset.listNames();
            while (names.hasNext()) result.add(names.next());
        } else if (SHDS.DEFAULT.getURI().equals(uri)) {
            result.add(null); // default graph
        } else {
            if (dataDataset.containsNamedModel(uri)) result.add(uri);
        }
        return result;
    }
}
