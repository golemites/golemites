package org.tablerocket.febo.plugin.application;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;

public class FeboAppPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getLogger().log(LogLevel.INFO,"Creating FEBO Fatjar.");
        Task generateFeboJar = project.getTasks().create( "generateFeboJar", GenerateFeboAppJarTask.class);

        // make compileJava depend on generating sourcecode
        project.getTasks().getByName("jar").dependsOn(generateFeboJar);
    }
}