package pe.chalk.minecraft;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-11-16
 */
public class Header {
    private String[] namespaces;
    private String className;
    private final List<String> functions;

    public Header(String className, String... functions){
        if(!className.contains("::")){
            this.namespaces = new String[]{};
            this.className = className;
        }else{
            this.namespaces = className.substring(0, className.lastIndexOf("::")).split("::");
            this.className = className.substring(className.lastIndexOf("::") + 2);
        }
        this.functions = new ArrayList<>(Arrays.asList(functions));
    }

    public String getClassName(){
        return this.className;
    }

    public List<String> getFunctions(){
        return this.functions;
    }

    public Header addFunction(String function){
        this.getFunctions().add(function);
        return this;
    }
    
    public String formatFunction(String functionText){
    	if(!functionText.contains("("))
    		return functionText;
    	
    	String classExterior = functionText.substring(0, functionText.indexOf("(") + 1) + functionText.substring(functionText.indexOf(")"));
    	String[] params = (functionText.substring(functionText.indexOf("(") + 1, functionText.indexOf(")"))).split(", ");
    	ArrayList<String> modifiedParams = new ArrayList<String>();
    	for(String param : params){
    		if(param.contains("const"))
    			param = "const " + param.substring(0, param.indexOf(" ")) + ((param.charAt(param.length() - 1) != 't') ? param.charAt(param.length() - 1) : "");
    		modifiedParams.add(param);
    	}
    	return classExterior.substring(0, classExterior.indexOf("(") + 1) + modifiedParams.toString().replace("[", "").replace("]", "") + classExterior.substring(classExterior.indexOf(")"));
    }

    public void save(){
        try{
            final Path path = Paths.get("out", this.getClassName() + ".h");
            if(Files.notExists(path.getParent())) Files.createDirectories(path.getParent());

            try(final BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)){
                writer.write("#pragma once"); writer.newLine();
                writer.newLine();

                for(String s : namespaces) {
                    writer.write("namespace " + s + " {"); writer.newLine();
                }

                writer.write("class " + this.getClassName() + " {"); writer.newLine();
                writer.write("public: "); writer.newLine();

                final String demangled = OnlineDemangler.demangle(this.getFunctions().parallelStream().collect(Collectors.joining("\n")));
                if(demangled != null) Arrays.stream(demangled.split("\\n")).parallel().map(function -> {
                    function = function.replaceAll("\\*", "*");

                    int open = function.indexOf('(');
                    if(open >= 0){
                        int index = function.lastIndexOf("::", open);
                        if(index >= 0 && index < open){
                            String methodClass = function.substring(0, index);
                            if(methodClass.contains("::")) methodClass = methodClass.substring(methodClass.lastIndexOf("::") + 2);
                            if(!methodClass.equals(this.getClassName())) return null;

                            function = function.substring(index + 2);
                        }
                    }

                    int close = function.lastIndexOf(" 1");
                    if(close >= 0) function = function.substring(0, close);

                    return function;
                }).filter(Objects::nonNull).forEachOrdered(function -> {
                    String prefix = "\t";
                    if(function.equals("__imp___cxa_pure_virtual") || function.equals("__cxa_pure_virtual")) prefix += "//";

                    prefix += "virtual ";

                    if(!function.startsWith("~")){
                    	if (function.startsWith("is") || function.startsWith("can"))
                    		prefix += "bool ";
                    	else
                    		prefix += "void ";
                    }
                    try{
                        writer.write(prefix + formatFunction(function) + ";"); writer.newLine();
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                });

                for (@SuppressWarnings("unused") String namespace : namespaces) {
                    writer.write("};"); writer.newLine();
                }

                writer.write("};");
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
