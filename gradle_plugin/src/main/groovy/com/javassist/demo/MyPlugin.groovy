package com.javassist.demo

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class MyPlugin implements Plugin<Project>{
    @Override
    void apply(Project project) {
        println "this is a myplugin"
        project.extensions.getByType(BaseExtension.class)
                .registerTransform(new MyTransform(project))
    }
}