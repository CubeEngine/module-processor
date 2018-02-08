/*
 * This file is part of CubeEngine.
 * CubeEngine is licensed under the GNU General Public License Version 3.
 *
 * CubeEngine is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CubeEngine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CubeEngine.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.cubeengine.processor;

import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.cubeengine.processor.PluginGenerator.CORE_ANNOTATION;
import static org.cubeengine.processor.PluginGenerator.DEP_ANNOTATION;
import static org.cubeengine.processor.PluginGenerator.PLUGIN_ANNOTATION;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;

@SupportedOptions({"cubeengine.module.version", "cubeengine.module.id", "cubeengine.module.name", "cubeengine.module.description", "cubeengine.module.team", "cubeengine.module.url", "cubeengine.module.libcube.version", "cubeengine.module.sponge.version"})
@SupportedAnnotationTypes({ PLUGIN_ANNOTATION, CORE_ANNOTATION, DEP_ANNOTATION })
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PluginGenerator extends AbstractProcessor
{

    private static final String PACKAGE = "org.cubeengine.processor.";
    static final String PLUGIN_ANNOTATION = PACKAGE + "Module";
    static final String CORE_ANNOTATION = PACKAGE + "Core";
    static final String DEP_ANNOTATION = PACKAGE + "Dependency";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (roundEnv.processingOver())
        {
            return false;
        }

        generateModulePlugin(roundEnv);
        generateCorePlugin(roundEnv);

        return false;
    }

    private void generateCorePlugin(RoundEnvironment roundEnv)
    {
        for (Element el : roundEnv.getElementsAnnotatedWith(Core.class))
        {
            buildSource((TypeElement) el, new ArrayList<>(), true);
        }
    }

    public void generateModulePlugin(RoundEnvironment roundEnv)
    {
        for (Element el : roundEnv.getElementsAnnotatedWith(Module.class))
        {
            final TypeElement element = (TypeElement) el;

            Module annotation = element.getAnnotation(Module.class);
            Dependency[] deps = annotation.dependencies();

            Dependency coreDep = getCoreDep();
            List<Dependency> allDeps = new ArrayList<>(Arrays.asList(deps));
            allDeps.add(coreDep);

            buildSource(element, allDeps, false);
        }
    }

    private void buildSource(TypeElement element, List<Dependency> allDeps, boolean core) {
        String dependencies = allDeps.stream().map(d -> String.format("@Dependency(id = \"%s\", version = \"%s\", optional = %s)", d.value(), d.version(), d.optional())).collect(Collectors.joining(",\n"));

        Name packageName = ((PackageElement) element.getEnclosingElement()).getQualifiedName();
        String pluginName = "Plugin" + element.getSimpleName();
        String simpleName = element.getSimpleName().toString();
        String moduleClass = packageName + "." + element.getSimpleName();
        String sourceVersion = processingEnv.getOptions().getOrDefault("cubeengine.module.sourceversion","unknown");
        if ("${githead.branch}-${githead.commit}".equals(sourceVersion))
        {
            sourceVersion = "unknown";
        }
        String version = processingEnv.getOptions().getOrDefault("cubeengine.module.version","unknown");
        String id = "cubeengine-" + processingEnv.getOptions().getOrDefault("cubeengine.module.id",element.getSimpleName().toString().toLowerCase());
        String name = "CubeEngine - " + processingEnv.getOptions().getOrDefault("cubeengine.module.name","unknown");
        String description = processingEnv.getOptions().getOrDefault("cubeengine.module.description","unknown");
        String team = processingEnv.getOptions().getOrDefault("cubeengine.module.team","unknown") + " Team";
        String url = processingEnv.getOptions().getOrDefault("cubeengine.module.url","");

        try (BufferedWriter writer = newSourceFile(packageName, pluginName))
        {
            writer.write("package " + packageName + ";\n\n");
            writer.write("import javax.inject.Inject;\n");
            writer.write("import com.google.inject.Injector;\n");
            writer.write("import org.spongepowered.api.plugin.Plugin;\n");
            writer.write("import org.spongepowered.api.plugin.Dependency;\n");
            writer.write("import org.cubeengine.libcube.CubeEnginePlugin;\n");
            if (!core) writer.write("import org.cubeengine.libcube.LibCube;\n");
            writer.write("import org.spongepowered.api.Sponge;\n");
            writer.write(String.format("import %s;\n", moduleClass));
            writer.write("\n");
            writer.write(String.format("@Plugin(id = %s.%s,\n"
                            + "        name = \"%s\",\n"
                            + "        version = %s.%s,\n"
                            + "        description = \"%s\",\n"
                            + "        url = \"%s\",\n"
                            + "        authors = \"%s\",\n"
                            + "        dependencies = {%s})\n",
                    pluginName, simpleName.toUpperCase() + "_ID",
                    name,
                    pluginName, simpleName.toUpperCase() + "_VERSION",
                    description,
                    url,
                    team,
                    dependencies));
            writer.write(String.format(
                    "public class %s extends CubeEnginePlugin\n"
                            + "{\n"
                            + "    public static final String %s = \"%s\";\n"
                            + "    public static final String %s = \"%s\";\n"
                            + "\n"
                            + "    public %s()\n"
                            + "    {\n"
                            + "         super(%s.class);\n"
                            + "    }\n"
                            + "\n"
                            + "    public String sourceVersion()\n"
                            + "    {\n"
                            + "        return \"%s\";\n"
                            + "    }\n"
                            + "}\n",
                    pluginName,
                    simpleName.toUpperCase() + "_ID",
                    id,
                    simpleName.toUpperCase() + "_VERSION",
                    version,
                    pluginName,
                    element.getSimpleName(),
                    sourceVersion));
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }

        try (BufferedWriter writer = newResourceFile("assets", id + "/lang/en_us.lang"))
        {
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public Dependency getCoreDep() {
        return new Dependency() {
            @Override
            public String value() {
                return "cubeengine-core";
            }

            @Override
            public String version() {
                return processingEnv.getOptions().getOrDefault("cubeengine.module.libcube.version", "unknown");
            }

            @Override
            public boolean optional() {
                return false;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Dependency.class;
            }
        };
    }

    private BufferedWriter newSourceFile(Name packageName, String pluginName) throws IOException
    {
        FileObject obj = this.processingEnv.getFiler().createSourceFile(packageName + "." + pluginName);
        return new BufferedWriter(obj.openWriter());
    }

    private BufferedWriter newResourceFile(String packageName, String fileName) throws IOException
    {
        FileObject obj = this.processingEnv.getFiler().createResource(CLASS_OUTPUT, packageName, fileName);
        return new BufferedWriter(obj.openWriter());
    }
}
