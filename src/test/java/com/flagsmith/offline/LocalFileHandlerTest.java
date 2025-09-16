package com.flagsmith.offline;

import com.flagsmith.FlagsmithTestHelper;
import com.flagsmith.exceptions.FlagsmithClientError;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class LocalFileHandlerTest {
    @Test
    public void testLocalFileHandler() throws FlagsmithClientError, IOException {
        // Given
        File file = File.createTempFile("temp",".txt");
        try (FileWriter fileWriter = new FileWriter(file, true)) {
          fileWriter.write(FlagsmithTestHelper.environmentString());
          fileWriter.flush();
        }

        // When
        LocalFileHandler handler = new LocalFileHandler(file.getAbsolutePath());

        // Then
        assertEquals(FlagsmithTestHelper.evaluationContext(), handler.getEvaluationContext());

        file.delete();
    }
}
