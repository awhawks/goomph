/*
 * Copyright 2016 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.gradle.eclipserunner;

import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.process.JavaExecSpec;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.diffplug.common.base.Errors;
import com.diffplug.gradle.JavaExecable;

/**
 * Runs an `EclipseApp` in a new JVM using a folder containing
 * a `plugins` folder with the necessary jars.
 */
public class JarFolderRunnerExternalJvm implements EclipseRunner {
	final File rootDirectory;
	@Nullable
	final Project project;

	/**
	 * If you have a gradle {@link Project} object handy, use
	 * {@link #JarFolderRunnerExternalJvm(File, Project)} instead,
	 * as it will be more reliable.  This constructor may fail
	 * if there are fancy classloaders at work.
	 *
	 * @param rootDirectory a directory which contains a `plugins` folder containing the OSGi jars needed to run applications.
	 */
	public JarFolderRunnerExternalJvm(File rootDirectory) {
		this(rootDirectory, null);
	}

	/**
	 * @param rootDirectory a directory which contains a `plugins` folder containing the OSGi jars needed to run applications.
	 * @param project used to calculate the classpath of the newly launched JVM
	 */
	public JarFolderRunnerExternalJvm(File rootDirectory, @Nullable Project project) {
		this.rootDirectory = Objects.requireNonNull(rootDirectory);
		this.project = project;
	}

	@Override
	public void run(List<String> args) throws Exception {
		RunOutside outside = new RunOutside(rootDirectory, args);
		Errors.constrainTo(Exception.class).run(() -> {
			if (project == null) {
				JavaExecable.execWithoutGradle(outside, this::modifyClassPath);
			} else {
				JavaExecable.exec(project, outside, this::modifyClassPath);
			}
		});
	}

	@SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT", justification = "FindBugs thinks that setClasspath() doesn't have a side effect, but it actually does.")
	private void modifyClassPath(JavaExecSpec execSpec) {
		FileCollection classpath = execSpec.getClasspath();
		execSpec.setClasspath(classpath.filter(file -> {
			// we need org.eclipse.osgi, org.osgi.core will ruin everything
			return !file.getName().startsWith("org.osgi.core");
		}));
	}

	/** Helper class for running outside this JVM. */
	@SuppressWarnings("serial")
	private static class RunOutside implements JavaExecable {
		final File rootFolder;
		final List<String> args;

		public RunOutside(File rootFolder, List<String> args) {
			this.rootFolder = rootFolder;
			this.args = args;
		}

		@Override
		public void run() throws Throwable {
			JarFolderRunner launcher = new JarFolderRunner(rootFolder);
			launcher.run(args);
		}
	}
}