package com.csp.plugin

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class CSPPlugin implements Plugin<Project>{
    @Override
    void apply(Project project) {
        println "this is a CSPPlugin"
        project.extensions.getByType(BaseExtension.class)
                .registerTransform(new CSPTransform(project))
    }
}