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
package org.ballerinax.knative.test.samples;

import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.Handlers;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.ballerinax.knative.exceptions.KnativePluginException;
import org.ballerinax.knative.test.utils.DockerTestException;
import org.ballerinax.knative.test.utils.KnativeTestUtils;
import org.ballerinax.knative.utils.KnativeUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.ballerinax.knative.KnativeConstants.DOCKER;
import static org.ballerinax.knative.KnativeConstants.KNATIVE;
import static org.ballerinax.knative.KnativeConstants.KUBERNETES;
import static org.ballerinax.knative.test.utils.KnativeTestUtils.getExposedPorts;

/**
 * Test cases for sample 1.
 */
public class Sample1Test extends SampleTest {

    private static final Path SOURCE_DIR_PATH = SAMPLE_DIR.resolve("sample1");
    private static final Path DOCKER_TARGET_PATH = SOURCE_DIR_PATH.resolve(DOCKER);
    private static final Path KUBERNETES_TARGET_PATH = SOURCE_DIR_PATH.resolve(KUBERNETES);
    private static final String DOCKER_IMAGE = "hello_world_knative:latest";
    private Service knativeService;

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {
        Assert.assertEquals(KnativeTestUtils.compileBallerinaFile(SOURCE_DIR_PATH, "hello_world_knative.bal"), 0);
        File artifactYaml = KUBERNETES_TARGET_PATH.resolve(KNATIVE).resolve("hello_world_knative.yaml").toFile();
        Assert.assertTrue(artifactYaml.exists());
        Handlers.register(new KnativeTestUtils.ServiceHandler());
        KubernetesClient client = new DefaultKubernetesClient();
        List<HasMetadata> k8sItems = client.load(new FileInputStream(artifactYaml)).get();
        for (HasMetadata data : k8sItems) {
            if ("Service".equals(data.getKind())) {
                this.knativeService = (Service) data;
            } else {
                Assert.fail("Unexpected k8s/knative resource found: " + data.getKind());
            }
        }
    }

    @Test
    public void validateDeployment() {
        Assert.assertNotNull(this.knativeService);
        Assert.assertEquals(this.knativeService.getMetadata().getName(), "helloworld-knative-svc");
        Assert.assertEquals(this.knativeService.getSpec().getTemplate().getSpec().getContainerConcurrency().longValue(),
                100);
        Assert.assertEquals(this.knativeService.getSpec().getTemplate().getSpec().getContainers().size(), 1);

        Container container = this.knativeService.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assert.assertEquals(container.getImage(), DOCKER_IMAGE);
        Assert.assertEquals(container.getPorts().size(), 1);
        Assert.assertEquals(container.getPorts().get(0).getContainerPort().intValue(), 8080);
        Assert.assertEquals(container.getPorts().get(0).getProtocol(), "TCP");
        Assert.assertEquals(container.getName(), "helloworld-knative-svc");
    }

    @Test
    public void validateDockerfile() {
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
    }

    @Test
    public void validateDockerImage() throws DockerTestException, InterruptedException {
        List<String> ports = getExposedPorts(DOCKER_IMAGE);
        Assert.assertEquals(ports.size(), 1);
        Assert.assertEquals(ports.get(0), "8080/tcp");
    }

    @AfterClass
    public void cleanUp() throws KnativePluginException {
        KnativeUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KnativeUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KnativeTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }
}
