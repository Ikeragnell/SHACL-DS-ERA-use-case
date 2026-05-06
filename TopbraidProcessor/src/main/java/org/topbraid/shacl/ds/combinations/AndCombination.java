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
import org.topbraid.shacl.ds.vocabulary.SHDS;

/**
 * Graph combination representing the intersection ({@code shds:and}) of a list of graphs.
 * The result contains only triples present in every operand graph.
 *
 * @author Davan Chiem Dao
 */
public class AndCombination extends GraphCombination {

    public AndCombination(Resource node, Model definingModel) {
        super(node, definingModel);
    }

    @Override
    public Model toModel(Dataset dataDataset) {
        List<GraphCombination> children = parseListChildren(SHDS.and);
        if (children.isEmpty()) {
            return ModelFactory.createDefaultModel();
        }
        Model result = children.get(0).toModel(dataDataset);
        for (int i = 1; i < children.size(); i++) {
            result = result.intersection(children.get(i).toModel(dataDataset));
        }
        return result;
    }
}
