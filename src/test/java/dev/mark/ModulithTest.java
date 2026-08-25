package dev.mark;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithTest {
    @Test
    void writeDocumentationSnippets() {
        // This analyzes your code and creates the diagrams
        ApplicationModules modules = ApplicationModules.of(MarkApplication.class);
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
