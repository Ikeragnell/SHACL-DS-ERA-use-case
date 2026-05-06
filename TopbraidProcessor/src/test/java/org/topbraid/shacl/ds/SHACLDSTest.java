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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;
import org.topbraid.shacl.ds.vocabulary.SHDS;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

/**
 * Integration tests for SHACL-DS validation via {@link ValidationUtil#validateDataset}.
 *
 * <p>Each test loads a {@code data.trig} and a {@code shapes.trig} from the shared
 * SHACL-DS test-case suite, runs validation, and compares the result against the
 * canonical {@code report.ttl} using a fingerprint-based comparison that is
 * insensitive to blank-node naming.
 *
 * <p>A fingerprint uniquely identifies a validation result by the combination of:
 * focus node, constraint component, result path, source shapes graph, and focus graph.
 *
 * @author Davan Chiem Dao
 */
public class SHACLDSTest {

    private static final String TEST_CASES_RESOURCE_BASE = "ds/";

    // --- Test cases ---

    @Test public void test_0000_SHDSDS()            { run("SHACL-DS-0000-SHDSDS"); }
    @Test public void test_0001_TG_ALL()             { run("SHACL-DS-0001-TG-ALL"); }
    @Test public void test_0001_TG_DEFAULT()         { run("SHACL-DS-0001-TG-DEFAULT"); }
    @Test public void test_0001_TG_IRI()             { run("SHACL-DS-0001-TG-IRI"); }
    @Test public void test_0001_TG_NAMED()           { run("SHACL-DS-0001-TG-NAMED"); }
    @Test public void test_0002_TGE()                { run("SHACL-DS-0002-TGE"); }
    @Test public void test_0003_TGC_AND()            { run("SHACL-DS-0003-TGC-AND"); }
    @Test public void test_0003_TGC_MINUS()          { run("SHACL-DS-0003-TGC-MINUS"); }
    @Test public void test_0003_TGC_OR()             { run("SHACL-DS-0003-TGC-OR"); }
    @Test public void test_0004_TGC_COMPLEX()        { run("SHACL-DS-0004-TGC-COMPLEX"); }
    @Test public void test_0005_SPARQL_COMBINATION() { run("SHACL-DS-0005-SPARQL-COMBINATION"); }
    @Test public void test_0005_SPARQL_DEFAULT()     { run("SHACL-DS-0005-SPARQL-DEFAULT"); }
    @Test public void test_0005_SPARQL_NAMED()       { run("SHACL-DS-0005-SPARQL-NAMED"); }
    @Test public void test_0006_SPARQL_DATASET_VIEW(){ run("SHACL-DS-0006-SPARQL-DATASET-VIEW"); }
    @Test public void test_0007_SPARQL_ASK()         { run("SHACL-DS-0007-SPARQL-ASK"); }
    @Test public void test_0007_SPARQL_SELECT()      { run("SHACL-DS-0007-SPARQL-SELECT"); }
    @Test public void test_0008_TG_PATTERN()         { run("SHACL-DS-0008-TG-PATTERN"); }

    // --- Core runner ---

    private void run(String name) {
        String dir = TEST_CASES_RESOURCE_BASE + name + "/";

        Dataset dataDataset   = loadDataset(dir + "data.trig");
        Dataset shapesDataset = loadDataset(dir + "shapes.trig");
        Model   expected      = loadModel(dir + "report.ttl");

        Resource actual = ValidationUtil.validateDataset(shapesDataset, dataDataset);

        Resource expectedReport = expected.listStatements(null, RDF.type, SH.ValidationReport)
            .nextStatement().getSubject();

        boolean expectedConforms = expected.listStatements(null, SH.conforms, (RDFNode) null)
            .nextOptional().map(Statement::getBoolean).orElse(true);
        boolean actualConforms = actual.getRequiredProperty(SH.conforms).getBoolean();

        Set<String> expectedFPs = fingerprints(expectedReport, expected);
        Set<String> actualFPs   = fingerprints(actual, actual.getModel());

        if (!expectedFPs.equals(actualFPs) || expectedConforms != actualConforms) {
            System.err.println("=== EXPECTED ===");
            RDFDataMgr.write(System.err, expected, RDFFormat.TURTLE_PRETTY);
            System.err.println("=== ACTUAL ===");
            RDFDataMgr.write(System.err, actual.getModel(), RDFFormat.TURTLE_PRETTY);
            System.err.println("=== EXPECTED FINGERPRINTS ===");
            expectedFPs.stream().sorted().forEach(f -> System.err.println("  " + f));
            System.err.println("=== ACTUAL FINGERPRINTS ===");
            actualFPs.stream().sorted().forEach(f -> System.err.println("  " + f));
        }

        Assert.assertEquals("[" + name + "] sh:conforms", expectedConforms, actualConforms);
        Assert.assertEquals("[" + name + "] result fingerprints", expectedFPs, actualFPs);
    }

    // --- Fingerprinting ---

    /**
     * Collects a fingerprint string for every {@code sh:result} in the report.
     * The set comparison is order-independent and blank-node-insensitive.
     */
    private Set<String> fingerprints(Resource report, Model model) {
        Set<String> fps = new HashSet<>();
        StmtIterator it = report.listProperties(SH.result);
        while (it.hasNext()) {
            fps.add(fingerprint(it.next().getObject().asResource(), model));
        }
        return fps;
    }

    /**
     * Produces a canonical string identifying one validation result, using the
     * five fields that uniquely characterise a SHACL-DS result:
     * focus node | constraint component | result path | source shapes graph | focus graph
     */
    private String fingerprint(Resource r, Model m) {
        return getIRI(r, SH.focusNode, m)                  + "|" +
               getIRI(r, SH.sourceConstraintComponent, m)  + "|" +
               getIRI(r, SH.resultPath, m)                 + "|" +
               getIRI(r, SHDS.sourceShapesGraph, m)        + "|" +
               focusGraphFingerprint(getNode(r, SHDS.focusGraph, m));
    }

    private String getIRI(Resource r, org.apache.jena.rdf.model.Property p, Model m) {
        Statement s = m.getProperty(r, p);
        if (s == null) return "";
        RDFNode o = s.getObject();
        return o.isURIResource() ? o.asResource().getURI() : "";
    }

    private RDFNode getNode(Resource r, org.apache.jena.rdf.model.Property p, Model m) {
        Statement s = m.getProperty(r, p);
        return s == null ? null : s.getObject();
    }

    /**
     * Produces a canonical string for a focus graph node, which may be:
     * <ul>
     *   <li>A named IRI — returned as-is.</li>
     *   <li>A blank node combination ({@code shds:or/and/minus}) — serialised recursively.</li>
     * </ul>
     */
    private String focusGraphFingerprint(RDFNode node) {
        if (node == null) return "";
        if (node.isURIResource()) return node.asResource().getURI();
        if (node.isAnon()) {
            Resource r = node.asResource();
            if (r.hasProperty(SHDS.or))    return "or("    + listFingerprint(r.getProperty(SHDS.or).getObject())    + ")";
            if (r.hasProperty(SHDS.and))   return "and("   + listFingerprint(r.getProperty(SHDS.and).getObject())   + ")";
            if (r.hasProperty(SHDS.minus)) return "minus(" + listFingerprint(r.getProperty(SHDS.minus).getObject()) + ")";
        }
        return node.toString();
    }

    private String listFingerprint(RDFNode listNode) {
        List<String> items = new ArrayList<>();
        Resource cur = listNode.asResource();
        while (cur != null && !cur.equals(RDF.nil)) {
            Statement first = cur.getProperty(RDF.first);
            if (first == null) break;
            items.add(focusGraphFingerprint(first.getObject()));
            Statement rest = cur.getProperty(RDF.rest);
            if (rest == null) break;
            cur = rest.getObject().asResource();
        }
        return String.join(",", items);
    }

    // --- Dataset / model loading ---

    private Dataset loadDataset(String resourcePath) {
        Dataset ds = DatasetFactory.createGeneral();
        String uri = resolveResource(resourcePath);
        RDFDataMgr.read(ds, uri);
        return ds;
    }

    private Model loadModel(String resourcePath) {
        Model m = ModelFactory.createDefaultModel();
        String uri = resolveResource(resourcePath);
        RDFDataMgr.read(m, uri);
        return m;
    }

    private String resolveResource(String resourcePath) {
        java.net.URL url = getClass().getClassLoader().getResource(resourcePath);
        if (url == null) {
            throw new IllegalArgumentException("Test resource not found on classpath: " + resourcePath);
        }
        return url.toString();
    }
}
