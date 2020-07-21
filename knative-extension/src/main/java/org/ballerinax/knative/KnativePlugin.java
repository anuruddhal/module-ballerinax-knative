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

import org.ballerinalang.compiler.JarResolver;
import org.ballerinalang.compiler.plugins.AbstractCompilerPlugin;
import org.ballerinalang.compiler.plugins.SupportedAnnotationPackages;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.FunctionNode;
import org.ballerinalang.model.tree.PackageNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;
import org.ballerinalang.util.diagnostic.Diagnostic;
import org.ballerinalang.util.diagnostic.DiagnosticLog;
import org.ballerinax.knative.exceptions.KnativePluginException;
import org.ballerinax.knative.models.KnativeContext;
import org.ballerinax.knative.models.KnativeDataHolder;
import org.ballerinax.knative.processors.KnativeAnnotationProcessorFactory;
import org.ballerinax.knative.utils.DependencyValidator;
import org.ballerinax.knative.utils.KnativeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.ballerinalang.compiler.SourceDirectory;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ballerinalang.compiler.JarResolver.JAR_RESOLVER_KEY;
import static org.ballerinax.docker.generator.utils.DockerGenUtils.extractJarName;
import static org.ballerinax.knative.KnativeConstants.DOCKER;
import static org.ballerinax.knative.KnativeConstants.KUBERNETES;
import static org.ballerinax.knative.utils.KnativeUtils.printError;

/**
 * Compiler plugin to generate knative artifacts.
 */
@SupportedAnnotationPackages(
        value = {"ballerinax/knative"}
)

public class KnativePlugin extends AbstractCompilerPlugin {

    private static final Logger pluginLog = LoggerFactory.getLogger(KnativePlugin.class);
    private DiagnosticLog dlog;
    private SourceDirectory sourceDirectory;

    @Override
    public void setCompilerContext(CompilerContext context) {
        this.sourceDirectory = context.get(SourceDirectory.class);
        if (this.sourceDirectory == null) {
            throw new IllegalArgumentException("source directory has not been initialized");
        }
        KnativeContext.getInstance().setCompilerContext(context);
    }

    @Override
    public void init(DiagnosticLog diagnosticLog) {
        this.dlog = diagnosticLog;
    }

    @Override
    public void process(PackageNode packageNode) {
        BLangPackage bPackage = (BLangPackage) packageNode;
        if (this.sourceDirectory == null) {
            throw new IllegalArgumentException("source directory has not been initialized");
        }
        KnativeContext.getInstance().addDataHolder(bPackage.packageID, sourceDirectory.getPath());
        //Get dependency jar paths
        JarResolver jarResolver = KnativeContext.getInstance().getCompilerContext().get(JAR_RESOLVER_KEY);
        if (jarResolver != null) {
            Set<Path> dependencyJarPaths = new HashSet<>(jarResolver.allDependencies(bPackage));
            KnativeContext.getInstance().getDataHolder(bPackage.packageID).getDockerModel()
                    .addDependencyJarPaths(dependencyJarPaths);
        }
    }

    @Override
    public void process(ServiceNode serviceNode, List<AnnotationAttachmentNode> annotations) {
        for (AnnotationAttachmentNode attachmentNode : annotations) {
            String annotationKey = attachmentNode.getAnnotationName().getValue();
            try {
                KnativeAnnotationProcessorFactory.getAnnotationProcessorInstance(annotationKey).processAnnotation
                        (serviceNode, attachmentNode);
            } catch (KnativePluginException e) {
                dlog.logDiagnostic(Diagnostic.Kind.ERROR, serviceNode.getPosition(), e.getMessage());
            }
        }
    }

    @Override
    public void process(SimpleVariableNode variableNode, List<AnnotationAttachmentNode> annotations) {
        if (!variableNode.getFlags().contains(Flag.LISTENER)) {
            dlog.logDiagnostic(Diagnostic.Kind.ERROR, variableNode.getPosition(), "@kubernetes annotations are only " +
                    "supported with listeners.");
            return;
        }
        for (AnnotationAttachmentNode attachmentNode : annotations) {
            String annotationKey = attachmentNode.getAnnotationName().getValue();
            try {
                KnativeAnnotationProcessorFactory.getAnnotationProcessorInstance(annotationKey).processAnnotation
                        (variableNode, attachmentNode);
            } catch (KnativePluginException e) {
                dlog.logDiagnostic(Diagnostic.Kind.ERROR, variableNode.getPosition(), e.getMessage());
            }
        }
    }

    @Override
    public void process(FunctionNode functionNode, List<AnnotationAttachmentNode> annotations) {
        for (AnnotationAttachmentNode attachmentNode : annotations) {
            String annotationKey = attachmentNode.getAnnotationName().getValue();
            try {
                KnativeAnnotationProcessorFactory.getAnnotationProcessorInstance(annotationKey).processAnnotation
                        (functionNode, attachmentNode);
            } catch (KnativePluginException e) {
                dlog.logDiagnostic(Diagnostic.Kind.ERROR, functionNode.getPosition(), e.getMessage());
            }
        }
    }

    @Override
    public void codeGenerated(PackageID moduleID, Path executableJarFile) {
        KnativeContext.getInstance().setCurrentPackage(moduleID);
        KnativeDataHolder dataHolder = KnativeContext.getInstance().getDataHolder();
        dataHolder.getDockerModel().setPkgId(moduleID);
        if (dataHolder.isCanProcess()) {
            executableJarFile = executableJarFile.toAbsolutePath();
            if (executableJarFile != null && Files.exists(executableJarFile)) {
                Path parent = executableJarFile.getParent();
                // artifacts location for a single bal file.
                Path knativeOutputPath = parent != null ? parent.resolve(KUBERNETES) : null;
                Path dockerOutputPath = parent != null ? parent.resolve(DOCKER) : null;
                if (Files.exists(executableJarFile)) {
                    // if executable came from a ballerina project
                    Path projectRoot = executableJarFile;
                    if (Files.exists(projectRoot.resolve("Ballerina.toml"))) {
                        dataHolder.setProject(true);
                        knativeOutputPath = projectRoot.resolve("target")
                                .resolve(KUBERNETES)
                                .resolve(extractJarName(executableJarFile));
                        dockerOutputPath = projectRoot.resolve("target")
                                .resolve(DOCKER)
                                .resolve(extractJarName(executableJarFile));
                    }
                }
                if (!dataHolder.getDockerModel().isUberJar()) {
                    JarResolver jarResolver =
                            KnativeContext.getInstance().getCompilerContext().get(JAR_RESOLVER_KEY);
                    executableJarFile = jarResolver.moduleJar(moduleID);
                }

                dataHolder.setUberJarPath(executableJarFile);
                dataHolder.setK8sArtifactOutputPath(knativeOutputPath);
                dataHolder.setDockerArtifactOutputPath(dockerOutputPath);
                KnativeArtifactManager knativeArtifactManager = new KnativeArtifactManager();
                try {
                    KnativeUtils.deleteDirectory(knativeOutputPath);
                    knativeArtifactManager.populateDeploymentModel();
                    validateDeploymentDependencies();
                    knativeArtifactManager.createArtifacts();
                } catch (KnativePluginException e) {
                    String errorMessage = "module [" + moduleID + "] " + e.getMessage();
                    printError(errorMessage);
                    pluginLog.error(errorMessage, e);
                    try {
                        KnativeUtils.deleteDirectory(knativeOutputPath);
                    } catch (KnativePluginException ignored) {
                        //ignored
                    }
                }
            } else {
                printError("error in resolving docker generation location.");
                pluginLog.error("error in resolving docker generation location.");
            }
        }
    }

    private void validateDeploymentDependencies() throws KnativePluginException {
        KnativeContext context = KnativeContext.getInstance();
        Map<PackageID, KnativeDataHolder> packageToDataHolderMap = context.getPackageIDtoDataHolderMap();
        DependencyValidator dependencyValidator = new DependencyValidator();
        for (KnativeDataHolder dataHolder : packageToDataHolderMap.values()) {
            //add other dependent deployments
            List<String> dependencies = new ArrayList<>();
            //add the current deployment as 0th element
            String currentDeployment = dataHolder.getServiceModel().getName();
            if (currentDeployment == null) {
                return;
            }
            dependencies.add(currentDeployment);
            Set<String> dependsOn = dataHolder.getServiceModel().getDependsOn();
            for (String listenerName : dependsOn) {
                String dependentDeployment = context.getDeploymentNameFromListener(listenerName);
                if (dependentDeployment == null) {
                    return;
                }
                if (!dependentDeployment.equals(currentDeployment)) {
                    dependencies.add(dependentDeployment);
                } else {
                    // Listener is in the same package.
                    throw new KnativePluginException("@kubernetes:Deployment{} contains cyclic dependencies");
                }
            }
            String[] array = dependencies.toArray(new String[0]);
            if (!dependencyValidator.validateDependency(array)) {
                throw new KnativePluginException("@kubernetes:Deployment{} contains cyclic dependencies");
            }
        }
    }
}
