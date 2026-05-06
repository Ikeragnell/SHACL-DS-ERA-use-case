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
package org.topbraid.shacl.ds.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Vocabulary constants for <a href="https://w3id.org/shacl-ds#">SHACL-DS</a>
 * (SHACL for Datasets).
 *
 * @author Davan Chiem Dao
 */
public class SHDS {

    public static final String NS = "https://w3id.org/shacl-ds#";

    public static final String PREFIX = "shds";


    // --- Special graph selectors ---

    /** {@code shds:all} — expands to the default graph plus all named graphs. */
    public static final Resource all = ResourceFactory.createResource(NS + "all");

    /** {@code shds:named} — expands to all named graphs (excludes the default graph). */
    public static final Resource named = ResourceFactory.createResource(NS + "named");

    /**
     * {@code shds:default} — refers to the original default graph.
     * During validation, the original default graph is preserved as a named graph
     * under this IRI so that SPARQL constraints can still reach it via
     * {@code GRAPH shds:default { ... }}.
     */
    public static final Resource DEFAULT = ResourceFactory.createResource(NS + "default");


    // --- Target graph selection ---

    /** {@code shds:targetGraph} — directly names a data graph that a shapes graph targets. */
    public static final Property targetGraph = ResourceFactory.createProperty(NS + "targetGraph");

    /** {@code shds:targetGraphPattern} — a regex matched against data graph IRIs to select targets. */
    public static final Property targetGraphPattern = ResourceFactory.createProperty(NS + "targetGraphPattern");

    /** {@code shds:targetGraphExclude} — directly names a data graph to exclude from targeting. */
    public static final Property targetGraphExclude = ResourceFactory.createProperty(NS + "targetGraphExclude");

    /** {@code shds:targetGraphExcludePattern} — a regex matched against data graph IRIs to exclude targets. */
    public static final Property targetGraphExcludePattern = ResourceFactory.createProperty(NS + "targetGraphExcludePattern");


    // --- Graph combinations ---

    /** {@code shds:targetGraphCombination} — declares a derived graph combination as the validation target. */
    public static final Property targetGraphCombination = ResourceFactory.createProperty(NS + "targetGraphCombination");

    /** {@code shds:or} — union of graphs in an RDF list. */
    public static final Property or = ResourceFactory.createProperty(NS + "or");

    /** {@code shds:and} — intersection of graphs in an RDF list. */
    public static final Property and = ResourceFactory.createProperty(NS + "and");

    /** {@code shds:minus} — set difference of exactly two graphs (minuend MINUS subtrahend). */
    public static final Property minus = ResourceFactory.createProperty(NS + "minus");


    // --- Validation report extensions ---

    /** {@code shds:sourceShapesGraph} — the named shapes graph that produced a validation result. */
    public static final Property sourceShapesGraph = ResourceFactory.createProperty(NS + "sourceShapesGraph");

    /** {@code shds:focusGraph} — the data graph (or combination) that was the focus of validation. */
    public static final Property focusGraph = ResourceFactory.createProperty(NS + "focusGraph");
}
