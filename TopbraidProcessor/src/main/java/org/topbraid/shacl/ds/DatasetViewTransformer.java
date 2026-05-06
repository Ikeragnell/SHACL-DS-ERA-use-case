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

import java.util.Iterator;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetImpl;
import org.topbraid.shacl.ds.vocabulary.SHDS;

/**
 * Creates a SHACL-DS dataset view for validation of a specific focus graph.
 *
 * <p>Per the SHACL-DS specification, before running SHACL validation against
 * a focus graph the dataset is restructured as follows:
 * <ol>
 *   <li>The original default graph is preserved as a named graph under
 *       {@code shds:default}, so that SPARQL constraints can still reach it
 *       via {@code GRAPH shds:default { ... }}.</li>
 *   <li>The focus graph becomes the new default graph, which is what SHACL's
 *       standard node/property shapes see.</li>
 *   <li>All other named graphs remain accessible via their IRIs.</li>
 * </ol>
 *
 * <p>Named graph models are shared by reference — no triple data is copied.
 * TopBraid's {@link org.topbraid.shacl.validation.ValidationEngine} already
 * passes the full {@link Dataset} to every {@code QueryExecution}, so SPARQL
 * constraints can reach all named graphs without any interception.
 *
 * @author Davan Chiem Dao
 */
public class DatasetViewTransformer {

    /**
     * Creates a dataset view where {@code focusGraphURI} is the default graph.
     *
     * @param original      the full data dataset
     * @param focusGraphURI the URI of the named graph to promote to default,
     *                      or {@code null} to keep the original default graph
     * @return a new {@link Dataset} configured as the SHACL-DS view
     */
    public static Dataset createView(Dataset original, String focusGraphURI) {
        Dataset view = new DatasetImpl(ModelFactory.createDefaultModel());

        // Copy all named graphs by reference
        Iterator<String> names = original.listNames();
        while (names.hasNext()) {
            String name = names.next();
            view.addNamedModel(name, original.getNamedModel(name));
        }

        // Preserve the original default graph under shds:default
        view.addNamedModel(SHDS.DEFAULT.getURI(), original.getDefaultModel());

        // Promote the focus graph to default
        if (focusGraphURI == null) {
            view.setDefaultModel(original.getDefaultModel());
        } else {
            view.setDefaultModel(original.getNamedModel(focusGraphURI));
        }

        return view;
    }

    /**
     * Creates a dataset view where a pre-materialised {@code combinedModel} is
     * the default graph. Used for {@code shds:targetGraphCombination} targets.
     *
     * @param original      the full data dataset (supplies the named graphs)
     * @param combinedModel the materialised combined graph to use as default
     * @return a new {@link Dataset} configured as the SHACL-DS view
     */
    public static Dataset createCombinationView(Dataset original, Model combinedModel) {
        Dataset view = new DatasetImpl(ModelFactory.createDefaultModel());

        Iterator<String> names = original.listNames();
        while (names.hasNext()) {
            String name = names.next();
            view.addNamedModel(name, original.getNamedModel(name));
        }

        view.addNamedModel(SHDS.DEFAULT.getURI(), original.getDefaultModel());
        view.setDefaultModel(combinedModel);

        return view;
    }
}
