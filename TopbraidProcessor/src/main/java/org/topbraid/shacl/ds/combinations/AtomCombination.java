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

import java.util.List;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;

/**
 * Leaf node of a graph combination tree: refers to a single named graph
 * (or a special selector such as {@code shds:default}, {@code shds:named},
 * {@code shds:all}).
 *
 * <p>When the node is a special selector that expands to multiple graphs
 * (i.e. {@code shds:all} or {@code shds:named}), their union is returned.
 *
 * @author Davan Chiem Dao
 */
public class AtomCombination extends GraphCombination {

    public AtomCombination(Resource node, Model definingModel) {
        super(node, definingModel);
    }

    @Override
    public Model toModel(Dataset dataDataset) {
        List<String> graphs = expandSelector(node.getURI(), dataDataset);
        if (graphs.isEmpty()) {
            return ModelFactory.createDefaultModel();
        }
        if (graphs.size() == 1) {
            return getModel(dataDataset, graphs.get(0));
        }
        // shds:all or shds:named as a combination operand — union all expanded graphs
        Model result = ModelFactory.createDefaultModel();
        for (String graphURI : graphs) {
            result.add(getModel(dataDataset, graphURI));
        }
        return result;
    }
}
