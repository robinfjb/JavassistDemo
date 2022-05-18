package com.javassist.demo

import org.gradle.api.Plugin
import org.gradle.api.Project

class MyPlugin implements Plugin<Project>{
    @Override
    void apply(Project project) {
        println "this is a myplugin"
        project.android.registerTransform(new MyTransform(project))

    }
}