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
package org.topbraid.shacl.ds.combinations;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.topbraid.shacl.ds.vocabulary.SHDS;

/**
 * Abstract base for SHACL-DS graph combinations ({@code shds:targetGraphCombination}).
 *
 * <p>A combination is a derived graph built from named graphs in the data dataset
 * using set operations: union ({@code shds:or}), intersection ({@code shds:and}),
 * or set difference ({@code shds:minus}). Combinations form a tree: each node is
 * either a leaf ({@link AtomCombination}) or an operator with a list of children.
 *
 * <p>Use {@link #parse(RDFNode, Model)} to instantiate the correct subtype from
 * an RDF description.
 *
 * @author Davan Chiem Dao
 * @see OrCombination
 * @see AndCombination
 * @see MinusCombination
 * @see AtomCombination
 */
public abstract class GraphCombination {

    /** The RDF resource representing this combination node (may be a blank node). */
    protected final Resource node;

    /** The model in which this combination node's triples live. */
    protected final Model definingModel;

    protected GraphCombination(Resource node, Model definingModel) {
        this.node = node;
        this.definingModel = definingModel;
    }

    /**
     * Materialises this combination against the given data dataset.
     *
     * @param dataDataset the full data dataset
     * @return a Jena {@link Model} containing the result triples
     */
    public abstract Model toModel(Dataset dataDataset);

    /**
     * Returns the RDF resource that represents this combination node in the shapes graph.
     * Used to annotate validation results with {@code shds:focusGraph}.
     */
    public Resource getNode() {
        return node;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Parses a combination node and returns the appropriate concrete subtype.
     *
     * @param node         the RDF node representing the combination
     * @param definingModel the model in which this node's triples live
     * @return the parsed {@link GraphCombination}
     * @throws IllegalArgumentException if node is not a resource
     */
    public static GraphCombination parse(RDFNode node, Model definingModel) {
        if (!node.isResource()) {
            throw new IllegalArgumentException("Graph combination node must be a resource: " + node);
        }
        Resource r = node.asResource();
        if (r.hasProperty(SHDS.or))    return new OrCombination(r, definingModel);
        if (r.hasProperty(SHDS.and))   return new AndCombination(r, definingModel);
        if (r.hasProperty(SHDS.minus)) return new MinusCombination(r, definingModel);
        return new AtomCombination(r, definingModel);
    }

    // -------------------------------------------------------------------------
    // Helpers for subclasses
    // -------------------------------------------------------------------------

    /**
     * Reads the RDF list attached to this node via {@code property} and returns
     * the parsed child {@link GraphCombination}s.
     *
     * @param property the operator property ({@code shds:or}, {@code shds:and}, or {@code shds:minus})
     * @return ordered list of child combinations; empty if the property is absent
     */
    protected List<GraphCombination> parseListChildren(org.apache.jena.rdf.model.Property property) {
        Statement stmt = node.getProperty(property);
        if (stmt == null) {
            return List.of();
        }
        RDFNode listNode = stmt.getObject();
        if (!listNode.isResource()) {
            return List.of();
        }
        RDFList list = listNode.asResource().as(RDFList.class);
        List<GraphCombination> children = new ArrayList<>();
        Iterator<RDFNode> it = list.iterator();
        while (it.hasNext()) {
            children.add(parse(it.next(), definingModel));
        }
        return children;
    }

    /**
     * Expands special selector IRIs ({@code shds:all}, {@code shds:named},
     * {@code shds:default}) into concrete graph URI strings.
     * A {@code null} entry in the returned list represents the default graph.
     *
     * @param uri         the URI string to expand
     * @param dataDataset the data dataset
     * @return list of concrete graph URIs; {@code null} entries denote the default graph
     */
    protected static List<String> expandSelector(String uri, Dataset dataDataset) {
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
            result.add(uri);
        }
        return result;
    }

    /**
     * Returns the {@link Model} for a graph URI, where {@code null} means the default graph.
     */
    protected static Model getModel(Dataset dataDataset, String graphURI) {
        return graphURI == null
            ? dataDataset.getDefaultModel()
            : dataDataset.getNamedModel(graphURI);
    }
}
