package com.asinkxcoswt.archetype.internal;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(Processor.class)
public class ArchetypeProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(Archetype.class)) {
            if (annotatedElement.getKind() != ElementKind.CLASS) {
                error(annotatedElement, "Only classes can be annotated with @%s", Archetype.class.getSimpleName());
                return true; // Exit processing
            }

            Archetype archetype = annotatedElement.getAnnotation(Archetype.class);
            String packageName = ((PackageElement) annotatedElement.getEnclosingElement()).getQualifiedName().toString();
            String className = annotatedElement.getSimpleName().toString() + "Test.java";

            if (targetFileAlreadyExists(packageName, className)) {
                note(annotatedElement, "The target file already exists, skip generation.");
                return true; // Exit processing
            }

            try {
                FileObject fileObject = getTargetFile(packageName, className, annotatedElement);
                MustacheFactory factory = new DefaultMustacheFactory();
                String templateFile = getTemplateFile(archetype.template());
                try (Reader reader = new InputStreamReader(new FileInputStream(templateFile), Charset.forName("UTF-8")); Writer writer = fileObject.openWriter()) {
                    Mustache mustache = factory.compile(reader, templateFile);
                    Map<String, Object> context = getContext(annotatedElement);
                    mustache.execute(writer, context);
                }

                return true; // Exit processing
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

        }
        return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Stream.of("com.asinkxcoswt.archetype.internal.Archetype").collect(Collectors.toSet());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private void error(Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }

    private void note(Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, String.format(msg, args), e);
    }

    private Map<String, Object> getContext(Element annotatedElement) {
        return new HashMap<>();
    }

    private boolean targetFileAlreadyExists(String packageName, String className) {
        try {
            return new File(this.processingEnv.getFiler().getResource(StandardLocation.SOURCE_OUTPUT, packageName, className).toUri()).exists();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FileObject getTargetFile(String packageName, String className, Element annotatedElement) {
        try {
            return this.processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, packageName, className, annotatedElement);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String getTemplateFile(String relativePath) {
        try {
            return this.processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", relativePath).toUri().getPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
