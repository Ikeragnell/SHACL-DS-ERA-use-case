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
 * Graph combination representing the union ({@code shds:or}) of a list of graphs.
 * The result contains every triple present in at least one of the operand graphs.
 *
 * @author Davan Chiem Dao
 */
public class OrCombination extends GraphCombination {

    public OrCombination(Resource node, Model definingModel) {
        super(node, definingModel);
    }

    @Override
    public Model toModel(Dataset dataDataset) {
        List<GraphCombination> children = parseListChildren(SHDS.or);
        Model result = ModelFactory.createDefaultModel();
        for (GraphCombination child : children) {
            result.add(child.toModel(dataDataset));
        }
        return result;
    }
}
