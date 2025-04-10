package com.robin.plugin

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.bytecode.ClassFile
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import org.apache.commons.io.IOUtils
import org.apache.commons.codec.digest.DigestUtils

class JavassistTransform extends Transform {
    def project
    def pool = ClassPool.default

    JavassistTransform(Project project) {
        this.project = project
    }

    //任务名
    @Override
    public String getName() {
        return "JavassistTransform";
    }

    //你想要处理的文件
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    //你想要处理的范围
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    //是否增量编译
    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);
        println "start transform"
            // 添加Android相关类路径
            pool.appendClassPath(project.android.bootClasspath[0].toString())
            
            // 使用辅助类添加CameraX相关类路径
            JavassistHelper.addCameraXClassPath(pool)

            def outputProvider = transformInvocation.outputProvider
            // 删除之前的输出
            if (outputProvider != null) {
                outputProvider.deleteAll()
            }
            //1.拿到需要的处理的class文件
            transformInvocation.getInputs().each { allInput ->
                //类最终生成为两种形式 1.文件夹（包含包名） 2.jar包
                allInput.directoryInputs.each { dirInput ->
//                def preClassNamePath = dirInput.file.absolutePath
//                println "class文件路径"+preClassNamePath
//                pool.insertClassPath(preClassNamePath)
//
//                findTarget(dirInput.file,preClassNamePath)

//                handleDirectory(dirInput.file)

                //2.获取输出的文件夹
                def dest = transformInvocation.outputProvider.getContentLocation(
                        dirInput.name,
                        dirInput.contentTypes,
                        dirInput.scopes,
                        Format.DIRECTORY)

                //把文件复制到下一个transform使用
                FileUtils.copyDirectory(dirInput.file, dest)
            }

            allInput.jarInputs.each { jarInput ->

                /*//2.获取输出的文件夹
                def dest = transformInvocation.outputProvider.getContentLocation(
                        jarInput.name,
                        jarInput.contentTypes,
                        jarInput.scopes,
                        Format.JAR)

                println "Jar包输出文件路径 " + dest
                //把文件复制到下一个transform使用
                FileUtils.copyFile(jarInput.file, dest)*/

                if (jarInput.file.exists()) {
                    def srcFile = handleJar(jarInput.file)

                    //必须给jar重新命名，否则会冲突
                    def jarName = jarInput.name
                    def md5 = DigestUtils.md5Hex(jarInput.file.absolutePath)
                    if (jarName.endsWith(".jar")) {
                        jarName = jarName.substring(0, jarName.length() - 4)
                    }
                    def dest = outputProvider.getContentLocation(md5 + jarName, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                    FileUtils.copyFile(srcFile, dest)
                }
            }

        }
    }


   /* void handleDirectory(File dir) {
        //将类路径添加到classPool中
        pool.insertClassPath(dir.absolutePath)
        if (dir.isDirectory()) {
            dir.eachFileRecurse { file ->
                def filePath = file.absolutePath
                pool.insertClassPath(filePath)
                if (shouldModify(filePath)) {
                    def inputStream = new FileInputStream(file)
                    CtClass ctClass = modifyClass(inputStream)
                    ctClass.writeFile()
                    //调用detach方法释放内存
                    ctClass.detach()
                }
            }
        }
    }*/

    /**
     * 主要步骤：
     * 1.遍历所有jar文件
     * 2.解压jar然后遍历所有的class
     * 3.读取class的输入流并使用javassit修改，然后保存到新的jar文件中
     */
    File handleJar(File jarFile) {
            pool.appendClassPath(jarFile.absolutePath)
            def inputJarFile = new JarFile(jarFile)
            def entries = inputJarFile.entries()
            //创建一个新的文件
            def outputJarFile = new File(jarFile.parentFile, "temp_" + jarFile.name)
            if (outputJarFile.exists()) outputJarFile.delete()
            def jarOutputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputJarFile)))
            
            // 如果两个类都已经处理过了，直接复制整个jar文件
            if (handledAgentWebConfig && handledCustomCameraView) {
                while (entries.hasMoreElements()) {
                    def jarInputEntry = entries.nextElement()
                    def outputJarEntry = new JarEntry(jarInputEntry.name)
                    jarOutputStream.putNextEntry(outputJarEntry)
                    def inputStream = inputJarFile.getInputStream(jarInputEntry)
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                    inputStream.close()
                }
                inputJarFile.close()
                jarOutputStream.closeEntry()
                jarOutputStream.flush()
                jarOutputStream.close()
                return outputJarFile
            }
            
            while (entries.hasMoreElements()) {
                def jarInputEntry = entries.nextElement()
                def jarInputEntryName = jarInputEntry.name
                
                def outputJarEntry = new JarEntry(jarInputEntryName)
                jarOutputStream.putNextEntry(outputJarEntry)
                
                def inputStream = inputJarFile.getInputStream(jarInputEntry)
                if (!shouldModify(jarInputEntryName)) {
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                    inputStream.close()
                    continue
                }
                def ctClass = modifyClass(inputStream)
                def byteCode = ctClass.toBytecode()
                ctClass.detach()
                jarOutputStream.write(byteCode)
                inputStream.close()
                jarOutputStream.flush()
                
                // 如果两个类都已经处理过了，直接复制剩余的条目
                if (handledAgentWebConfig && handledCustomCameraView) {
                    while (entries.hasMoreElements()) {
                        jarInputEntry = entries.nextElement()
                        outputJarEntry = new JarEntry(jarInputEntry.name)
                        jarOutputStream.putNextEntry(outputJarEntry)
                        inputStream = inputJarFile.getInputStream(jarInputEntry)
                        jarOutputStream.write(IOUtils.toByteArray(inputStream))
                        inputStream.close()
                    }
                    break
                }
            }
            
            inputJarFile.close()
            jarOutputStream.closeEntry()
            jarOutputStream.flush()
            jarOutputStream.close()
            return outputJarFile
    }

    static boolean shouldModify(String filePath) {
        return filePath.endsWith(".class") &&
                !filePath.contains("R.class") &&
                !filePath.contains("BuildConfig.class") &&
                ((filePath.contains("AgentWebConfig.class")) ||
                        (filePath.contains("CustomCameraView\$DisplayListener")))
    }

    CtClass modifyClass(InputStream is) {
            def classFile = new ClassFile(new DataInputStream(new BufferedInputStream(is)))
            def className = classFile.name
            // 尝试导入可能需要的包
            try {
                pool.importPackage("androidx.camera.view")
                pool.importPackage("androidx.camera.core")
            } catch (Exception e) {
                println "导入包失败: " + e.getMessage()
            }
            
            def ctClass = pool.get(className)
            //判断是否需要解冻
            if (ctClass.isFrozen()) {
                ctClass.defrost()
            }
            
            String methodName = ""
            println "处理类: " + ctClass.name
            
            if (ctClass.name.contains("AgentWebConfig")) {
                methodName = "debug"
//                try {
                    def method = ctClass.getDeclaredMethod(methodName)
                    deleteCodeInMethod(method)
//                } catch (Exception e) {
//                    println "修改AgentWebConfig中的debug方法失败: " + e.getMessage()
//                    e.printStackTrace()
//                }
            } else if (ctClass.name.contains("CustomCameraView\$DisplayListener")) {
                methodName = "onDisplayChanged"
//                try {
                    def method = ctClass.getDeclaredMethod(methodName)
                    ModifyCustomCameraViewCodeInMethod(method)
//                } catch (Exception e) {
//                    println "修改DisplayListener中的onDisplayChanged方法失败: " + e.getMessage()
//                    e.printStackTrace()
//                }
            }

            return ctClass
    }

    /**
     *找到class结尾的文件
     * @param dir
     * @param fileNamePath  >>app\build\intermediates\javac\release\classes
     */
   /* private void findTarget(File dir, String fileNamePath) {
        if (dir.isDirectory()) {
            dir.listFiles().each {
                findTarget(it, fileNamePath)
            }
        }else {
            def filePath = dir.absolutePath
            if (filePath.endsWith(".class")) {
                println "找到Class"+filePath
                //修改文件
                modify(filePath, fileNamePath)
            }
        }

    }*/
  /*  private void modify(String filePath, String fileNamePath) {
        //过滤没用的文件
        if (filePath.contains('R$') || filePath.contains('R.class')
                || filePath.contains("BuildConfig.class")) {
            return
        }
        println "开始修改Class"+filePath

        //因为Javassist需要class包名也就是》》com.example.javassist.MainActivity
        def className =  filePath.replace(fileNamePath, "")
                .replace("\\", ".")  .replace("/", ".")
        def name = className.replace(".class", "").substring(1)
        println "包名为:" + name
        //把class添加到pool中，才能修改class文件
        project.android.bootClasspath.each {
            pool.appendClassPath(it.absolutePath)
        }
        CtClass ctClass=  pool.get(name)
        //添加插入代码
        addCode(ctClass, fileNamePath)
    }*/

    /*private void addCode(CtClass ctClass ,String fileName) {
        //使class变成可修改
        ctClass.defrost()
        //获取class所有的方法
        CtMethod[] methods = ctClass.getDeclaredMethods()
        for (method in methods) {
            println "method "+method.getName()+"  参数个数  "+method.getParameterTypes().length
            if (method.getName().matches("hello")){
                method.addLocalVariable("start",CtClass.longType);
                method.insertBefore("{ start = System.currentTimeMillis();}");
                method.insertAfter("{ " +
                        " long last =  System.currentTimeMillis() - start;"+
                        "System.out.println(\" 方法耗时：\"+last);" +
                        "}");
            }

        }

        for (method in methods){
            println "deleteCodeInMethod start method"+method
            deleteCodeInMethod(method)
        }

        //把修改的内容写入文件
        ctClass.writeFile(fileName)
        //释放内存
        ctClass.detach()
    }*/

    def handledAgentWebConfig = false
    private void deleteCodeInMethod (CtMethod method){
        println "开始重写:"+method.name
        method.setBody("{DEBUG = true;}")
        handledAgentWebConfig = true
        /*method.instrument(new ExprEditor(){
            @Override
            void edit(MethodCall m) throws CannotCompileException {
                println("getClassName: "+ m.getClassName()+
                        " getMethodName: "+m.getMethodName() +
                        " line: " + m.getLineNumber());
                if (m.getClassName().matches(".*Log") && m.getMethodName().matches("e")){
                   println "modify>>>>>"
                   m.replace("{\$_;}")
                }
            }
        })*/
    }

    def handledCustomCameraView = false
    private void ModifyCustomCameraViewCodeInMethod(CtMethod method){
        println "开始重写:"+method.name
        CtClass etype = ClassPool.getDefault().get("java.lang.Exception")
        method.addCatch("{ System.out.println(\$e); return;}", etype)
        handledCustomCameraView = true
         /*   // 先尝试将PreviewView类添加到ClassPool中
            pool.importPackage("androidx.camera.view")
            
            method.setBody("""
                {
                System.out.println(\"fjb-->onDisplayChanged\" + this\$0.displayId);
                if ((int)\$1 == this\$0.displayId) {
                    if (this\$0.mImageCapture != null && this\$0.mCameraPreviewView.getDisplay() != null) {
                        this\$0.mImageCapture.setTargetRotation(this\$0.mCameraPreviewView.getDisplay().getRotation());
                    }
                    if (this\$0.mImageAnalyzer != null && this\$0.mCameraPreviewView.getDisplay() != null) {
                        this\$0.mImageAnalyzer.setTargetRotation(this\$0.mCameraPreviewView.getDisplay().getRotation());
                    }
                }
                }
            """)*/
    }
}
