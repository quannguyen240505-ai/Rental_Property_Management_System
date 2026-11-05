package com.rental.registry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
public class ModuleSmokeTest {
    @Test void moduleHasApplicationClass() { assertNotNull(ServiceRegistryApplication.class); }
}
