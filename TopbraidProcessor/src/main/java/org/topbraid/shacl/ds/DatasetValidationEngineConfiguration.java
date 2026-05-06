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

import org.topbraid.shacl.validation.ValidationEngineConfiguration;

/**
 * Extends {@link ValidationEngineConfiguration} with SHACL-DS-specific options.
 *
 * @author Davan Chiem Dao
 */
public class DatasetValidationEngineConfiguration extends ValidationEngineConfiguration {

    private boolean validateShapesDataset = false;

    /**
     * Returns whether the shapes dataset should be pre-validated against the
     * SHACL-DS meta-shapes before the main data validation runs.
     */
    public boolean isValidateShapesDataset() {
        return validateShapesDataset;
    }

    /**
     * When {@code true}, validates the shapes dataset against the SHACL-DS meta-shapes
     * ({@code shacl-ds.trig}) before running the main data validation.
     * If the shapes dataset is non-conforming the method returns the shapes validation
     * report immediately without running data validation.
     *
     * @return this configuration (fluent)
     */
    public DatasetValidationEngineConfiguration setValidateShapesDataset(boolean validateShapesDataset) {
        this.validateShapesDataset = validateShapesDataset;
        return this;
    }

    @Override
    public DatasetValidationEngineConfiguration setValidateShapes(boolean validateShapes) {
        super.setValidateShapes(validateShapes);
        return this;
    }
}
