package com.robin.plugin

import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.NotFoundException

import java.lang.reflect.Modifier

/**
 * Javassist辅助类，用于添加类路径和处理类查找
 */
class JavassistHelper {
    
    /**
     * 向ClassPool中添加CameraX相关的类路径
     * @param pool ClassPool实例
     */
    static void addCameraXClassPath(ClassPool pool) {
        try {
            // 导入CameraX相关包
            pool.importPackage("androidx.camera.view")
            pool.importPackage("androidx.camera.core")
            pool.importPackage("androidx.camera.lifecycle")
            pool.importPackage("androidx.camera.camera2")
            
            // 创建临时的PreviewView类，以防在处理过程中找不到
          /*  try {
                pool.getCtClass("androidx.camera.view.PreviewView")
            } catch (NotFoundException e) {
                println "创建临时PreviewView类"
                CtClass previewViewClass = pool.makeClass("androidx.camera.view.PreviewView")
                CtMethod ctMethod = new CtMethod(CtClass.metaClass, "getDisplay", null, previewViewClass)
                ctMethod.setModifiers(Modifier.PUBLIC)
                previewViewClass.stopPruning(true) // 防止自动删除
            }*/
            
            println "成功添加CameraX类路径"
        } catch (Exception e) {
            println "添加CameraX类路径失败: " + e.getMessage()
            e.printStackTrace()
        }
    }
} 