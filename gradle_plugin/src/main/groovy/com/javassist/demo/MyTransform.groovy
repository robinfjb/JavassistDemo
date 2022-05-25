package com.javassist.demo

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import javassist.CannotCompileException
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import org.gradle.api.Project

class MyTransform extends Transform {
    def project
    def pool = ClassPool.default

    MyTransform(Project project) {
        this.project = project
    }

    //任务名
    @Override
    public String getName() {
        return "MyTransform";
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
        //1.拿到需要的处理的class文件
        transformInvocation.getInputs().each { allInput ->
            //类最终生成为两种形式 1.文件夹（包含包名） 2.jar包
            allInput.directoryInputs.each { dirInput ->
                def preClassNamePath = dirInput.file.absolutePath
                println "class文件路径"+preClassNamePath
                pool.insertClassPath(preClassNamePath)

                findTarget(dirInput.file,preClassNamePath)

                //2.获取输出的文件夹
                def dest = transformInvocation.outputProvider.getContentLocation(
                        dirInput.name,
                        dirInput.contentTypes,
                        dirInput.scopes,
                        Format.DIRECTORY)

                println "文件夹输出文件路径 " + dest
                //把文件复制到下一个transform使用
                FileUtils.copyDirectory(dirInput.file, dest)
            }

            allInput.jarInputs.each { jarInput ->
                //2.获取输出的文件夹
                def dest = transformInvocation.outputProvider.getContentLocation(
                        jarInput.name,
                        jarInput.contentTypes,
                        jarInput.scopes,
                        Format.JAR)

                println "Jar包输出文件路径 " + dest
                //把文件复制到下一个transform使用
                FileUtils.copyFile(jarInput.file, dest)
            }

        }


        //3.
    }
    /**
     *找到class结尾的文件
     * @param dir
     * @param fileNamePath  >>app\build\intermediates\javac\release\classes
     */
    private void findTarget(File dir, String fileNamePath) {
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

    }
    private void modify(String filePath, String fileNamePath) {
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
    }

    private void addCode(CtClass ctClass ,String fileName) {
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
    }

    private void deleteCodeInMethod(CtMethod method){
        method.instrument(new ExprEditor(){
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
        })
    }
}
