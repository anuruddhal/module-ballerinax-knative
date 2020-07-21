/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinax.knative.models;

import java.util.Map;
import java.util.Objects;

/**
 * Model class to hold Knative config map data.
 */
public class ConfigMapModel extends KnativeModel {

    private Map<String, String> data;
    private String mountPath;
    private boolean readOnly;
    private String ballerinaConf;

    public ConfigMapModel() {
        this.readOnly = true;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getBallerinaConf() {
        return ballerinaConf;
    }

    public void setBallerinaConf(String ballerinaConf) {
        this.ballerinaConf = ballerinaConf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConfigMapModel)) {
            return false;
        }
        ConfigMapModel that = (
                ConfigMapModel) o;
        return Objects.equals(getMountPath(), that.getMountPath());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMountPath());
    }
}
