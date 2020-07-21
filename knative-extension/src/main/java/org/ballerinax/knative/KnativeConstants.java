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

package org.ballerinax.knative;

/**
 * Constants used in Knative extension.
 */
public class KnativeConstants {
    public static final String KUBERNETES = "kubernetes";
    public static final String KNATIVE = "knative";
    public static final String MAIN_FUNCTION_NAME = "main";
    public static final String KUBERNETES_SVC_PROTOCOL = "TCP";
    public static final String KUBERNETES_SELECTOR_KEY = "app";
    public static final String CONFIG_MAP_POSTFIX = "-config-map";
    public static final String SECRET_POSTFIX = "-secret";
    public static final String DOCKER = "docker";
    public static final String EXECUTABLE_JAR = ".jar";
    public static final String DEPLOYMENT_POSTFIX = "-deployment";
    public static final String SECRET_FILE_POSTFIX = "_secret";
    public static final String CONFIG_MAP_FILE_POSTFIX = "_config_map";
    public static final String RESOURCE_QUOTA_FILE_POSTFIX = "_resource_quota";
    public static final String KNATIVE_SVC_FILE_POSTFIX = "_knative_svc";
    public static final String YAML = ".yaml";
    public static final String DOCKER_LATEST_TAG = ":latest";
    public static final String BALLERINA_HOME = "/home/ballerina";
    public static final String BALLERINA_RUNTIME = "/ballerina/runtime";
    public static final String BALLERINA_CONF_MOUNT_PATH = "/home/ballerina/conf/";
    public static final String BALLERINA_CONF_FILE_NAME = "ballerina.conf";
    public static final String DOCKER_HOST = "DOCKER_HOST";
    public static final String DOCKER_CERT_PATH = "DOCKER_CERT_PATH";
    public static final String KNATIVE_SVC_POSTFIX = "-knative-svc";
    public static final String VOLUME_DEFINE = "-volume";


    /**
     * ImagePullPolicy type enum.
     */
    public enum ImagePullPolicy {
        IfNotPresent,
    }

    /**
     * Service type enum.
     */
    public enum ServiceType {
        ClusterIP
    }
}
