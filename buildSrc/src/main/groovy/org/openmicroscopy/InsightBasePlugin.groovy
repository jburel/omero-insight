/*
 * -----------------------------------------------------------------------------
 *  Copyright (C) 2019 University of Dundee & Open Microscopy Environment.
 *  All rights reserved.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * ------------------------------------------------------------------------------
 */
package org.openmicroscopy

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

import java.text.SimpleDateFormat

@CompileStatic
class InsightBasePlugin implements Plugin<Project> {

    public static final String TASK_PROCESS_CONFIGS = "processConfigs"

    public static final String TASK_OMERO_IMAGEJ_JAR = "imageJJar"

    public static final String MAIN_INSIGHT = "org.openmicroscopy.shoola.Main"

    public static final String MAIN_IMAGEJ = "org.openmicroscopy.shoola.MainIJPlugin"

    public static final String GROUP_BUILD = BasePlugin.BUILD_GROUP

    public static final List DEFAULT_JVM_ARGS = ["-Xms256m", "-Xmx1024m"]

    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.pluginManager.apply(JavaPlugin)

        configureJarTask()
        addProcessConfigs()
        addCreateImageJJar()
    }

    /**
     * Modifies the manifest of the default jar task from the Gradle JavaPlugin
     */
    private void configureJarTask() {
        project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar).configure { Jar jar ->
            jar.doFirst(addManifest(MAIN_INSIGHT))
        }
    }

    /**
     * Creates a jar similar to processResources in that it
     * copies config files from src dir to build dir.
     */
    private TaskProvider<Sync> addProcessConfigs() {
        TaskProvider<Sync> processConfigs = project.tasks.register(TASK_PROCESS_CONFIGS, Sync, new Action<Sync>() {
            @Override
            void execute(Sync sync) {
                sync.setDescription("Copies xml config files from src to build directory")
                sync.from("${project.projectDir}/src/config")
                sync.into("${project.buildDir}/config")
            }
        })

        project.tasks.named(JavaPlugin.CLASSES_TASK_NAME).configure { Task task ->
            task.dependsOn(processConfigs)
        }

        processConfigs
    }

    private TaskProvider<Jar> addCreateImageJJar() {
        JavaPluginConvention javaPluginConvention =
                project.convention.getPlugin(JavaPluginConvention)

        SourceSet main =
                javaPluginConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

        project.tasks.register(TASK_OMERO_IMAGEJ_JAR, Jar, new Action<Jar>() {
            @Override
            void execute(Jar jar) {
                // This might not be the best way to ensure a parity of names
                Jar jarTask = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
                String imageJName = jarTask.archiveBaseName.get().replace("insight", "imagej")

                jar.setDescription("Assembles a jar for use with ImageJ")
                jar.setGroup(GROUP_BUILD)
                jar.dependsOn(project.tasks.getByName(JavaPlugin.CLASSES_TASK_NAME))
                jar.from(main.output)
                jar.archiveBaseName.set(imageJName)
                jar.doFirst(addManifest(MAIN_IMAGEJ))
            }
        })
    }

    private Action<? extends Task> addManifest(String mainClass) {
        return new Action<Jar>() {
            @Override
            void execute(Jar jar) {
                jar.manifest.attributes(createBasicManifest(mainClass))
            }
        }
    }

    private Map<String, ?> createBasicManifest(String mainClass) {
        return ["Implementation-Title"  : project.name.replace("[^A-Za-z0-9]", ""),
                "Implementation-Version": project.version,
                "Built-Date"            : new SimpleDateFormat("dd/MM/yyyy").format(new Date()),
                "Built-JDK"             : System.getProperty("java.version"),
                "Built-Gradle"          : project.gradle.gradleVersion,
                "Main-Class"            : mainClass,
                "Class-Path"            : Utils.getRuntimeClasspathConfiguration(project).collect { it.name }.join(" ")]
    }

}
