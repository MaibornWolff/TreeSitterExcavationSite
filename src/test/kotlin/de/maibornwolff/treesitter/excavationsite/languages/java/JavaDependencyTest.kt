package de.maibornwolff.treesitter.excavationsite.languages.java

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JavaDependencyTest {
    @Nested
    inner class PackageExtraction {
        @Test
        fun `should extract package path`() {
            // Arrange
            val code = """
            package com.example.service;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.packagePath).containsExactly("com", "example", "service")
        }

        @Test
        fun `should extract single-segment package path`() {
            // Arrange
            val code = """
            package mypackage;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.packagePath).containsExactly("mypackage")
        }

        @Test
        fun `should return empty package path when no package declaration`() {
            // Arrange
            val code = "public class MyService {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.packagePath).isEmpty()
        }
    }

    @Nested
    inner class ImportExtraction {
        @Test
        fun `should extract single import`() {
            // Arrange
            val code = """
            import java.util.List;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util", "List")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract multiple imports`() {
            // Arrange
            val code = """
            import java.util.List;
            import java.util.Map;
            import java.io.File;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(3)
            assertThat(result.imports[0].path).containsExactly("java", "util", "List")
            assertThat(result.imports[1].path).containsExactly("java", "util", "Map")
            assertThat(result.imports[2].path).containsExactly("java", "io", "File")
        }

        @Test
        fun `should extract wildcard import`() {
            // Arrange
            val code = """
            import java.util.*;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should extract static import`() {
            // Arrange
            val code = """
            import static java.util.Collections.emptyList;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util", "Collections", "emptyList")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract static wildcard import`() {
            // Arrange
            val code = """
            import static java.util.Collections.*;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util", "Collections")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should return empty imports when no import declarations`() {
            // Arrange
            val code = """
            package com.example;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).isEmpty()
        }
    }

    @Nested
    inner class DeclarationExtraction {
        @Test
        fun `should return empty declarations when no type declarations`() {
            // Arrange
            val code = """
            package com.example;

            import java.util.List;
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.declarations).isEmpty()
        }

        @Test
        fun `should extract all declaration types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {}
            enum MyEnum {}
            record MyRecord() {}
            @interface MyAnnotation {}
            interface MyInterface {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.declarations).hasSize(5)
            val byName = result.declarations.associateBy { it.name }
            assertThat(byName["MyClass"]?.type).isEqualTo(DeclarationType.CLASS)
            assertThat(byName["MyEnum"]?.type).isEqualTo(DeclarationType.ENUM)
            assertThat(byName["MyRecord"]?.type).isEqualTo(DeclarationType.RECORD)
            assertThat(byName["MyAnnotation"]?.type).isEqualTo(DeclarationType.ANNOTATION)
            assertThat(byName["MyInterface"]?.type).isEqualTo(DeclarationType.INTERFACE)
        }

        @Test
        fun `should extract field types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                private String name;
                private int age;
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("String"), UsedType("int"))
        }

        @Test
        fun `should extract variable types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                public void myMethod() {
                    String name = "test";
                    int age = 42;
                }
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("String"), UsedType("int"), UsedType("void"))
        }

        @Test
        fun `should extract annotation types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                @Override
                @SuppressWarnings("unused")
                public void myMethod() {}
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(
                UsedType("Override"),
                UsedType("SuppressWarnings"),
                UsedType("void")
            )
        }

        @Test
        fun `should extract superclass`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass extends SuperClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("SuperClass"))
        }

        @Test
        fun `should extract implemented interfaces`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass implements Interface1, Interface2 {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("Interface1"), UsedType("Interface2"))
        }

        @Test
        fun `should extract extended interfaces`() {
            // Arrange
            val code = """
            package com.example;

            public interface MyInterface extends Interface1, Interface2 {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("Interface1"), UsedType("Interface2"))
        }

        @Test
        fun `should extract constructor call types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                public void someMethod() {
                    new SomeClass();
                }
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("SomeClass"), UsedType("void"))
        }

        @Test
        fun `should extract uppercase method invocation types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                public void someMethod() {
                    SomeClass.someMethod();
                    myVar.use();
                }
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("SomeClass"), UsedType("void"))
        }

        @Test
        fun `should extract uppercase field access types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                public void someMethod() {
                    MyEnum.ENUM_VALUE;
                }
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("MyEnum"), UsedType("void"))
        }

        @Test
        fun `should extract thrown types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                public void myMethod() throws IllegalArgumentException, IllegalStateException {}
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(
                UsedType("IllegalArgumentException"),
                UsedType("IllegalStateException"),
                UsedType("void")
            )
        }

        @Test
        fun `should extract method return types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                public String getName() { return "name"; }
                public int getAge() { return 42; }
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("String"), UsedType("int"))
        }

        @Test
        fun `should extract method parameter types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                public void setName(String name) {}
                public void setAge(int age) {}
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("String"), UsedType("int"), UsedType("void"))
        }

        @Test
        fun `should extract constructor parameter types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                public MyClass(String name, int age) {}
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("String"), UsedType("int"))
        }

        @Test
        fun `should extract generic types`() {
            // Arrange
            val code = """
            package com.example;

            public class MyClass {
                private List<String> names;
                private Map<String, Integer> nameToAge;
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(
                UsedType("List", listOf(UsedType("String"))),
                UsedType("Map", listOf(UsedType("String"), UsedType("Integer")))
            )
        }

        @Test
        fun `should exclude unparsable types from results`() {
            // Arrange - standalone method calls have no object node, producing a null TSNode
            val code = """
            package com.example;

            public class MyClass {
                public void myMethod() {
                    doSomething();
                }
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val usedTypes = result.declarations.first().usedTypes
            assertThat(usedTypes).containsExactlyInAnyOrder(UsedType("void"))
            assertThat(usedTypes.none { it.name.contains("unparsable") }).isTrue()
        }

        @Test
        fun `should scope used types per declaration`() {
            // Arrange
            val code = """
            package com.example;

            public class A {
                private B b;
            }

            public class B {
                private A a;
            }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            val byName = result.declarations.associateBy { it.name }
            assertThat(byName["A"]?.usedTypes).containsExactlyInAnyOrder(UsedType("B"))
            assertThat(byName["B"]?.usedTypes).containsExactlyInAnyOrder(UsedType("A"))
        }
    }

    @Nested
    inner class ApiSupportCheck {
        @Test
        fun `should report Java as supported for dependency analysis`() {
            // Act & Assert
            assertThat(TreeSitterDependencies.isDependencyAnalysisSupported(Language.JAVA)).isTrue()
        }

        @Test
        fun `should report unsupported language`() {
            // Act & Assert
            assertThat(TreeSitterDependencies.isDependencyAnalysisSupported(Language.PYTHON)).isFalse()
        }
    }
}
