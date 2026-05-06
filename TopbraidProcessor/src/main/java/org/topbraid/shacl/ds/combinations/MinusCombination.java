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
 * Graph combination representing set difference ({@code shds:minus}) of exactly two graphs.
 * The result contains triples present in the first operand (minuend) but not in the second
 * (subtrahend).
 *
 * @author Davan Chiem Dao
 */
public class MinusCombination extends GraphCombination {

    public MinusCombination(Resource node, Model definingModel) {
        super(node, definingModel);
    }

    @Override
    public Model toModel(Dataset dataDataset) {
        List<GraphCombination> children = parseListChildren(SHDS.minus);
        if (children.size() != 2) {
            throw new IllegalStateException(
                "shds:minus requires exactly 2 operands, got " + children.size() + " at " + node);
        }
        Model minuend    = children.get(0).toModel(dataDataset);
        Model subtrahend = children.get(1).toModel(dataDataset);
        return minuend.difference(subtrahend);
    }
}
