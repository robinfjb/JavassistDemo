package com.robin.plugin

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class JavassistPlugin implements Plugin<Project>{
    @Override
    void apply(Project project) {
        println "this is a JavassistPlugin"
        project.extensions.getByType(BaseExtension.class)
                .registerTransform(new JavassistTransform(project))
    }
}