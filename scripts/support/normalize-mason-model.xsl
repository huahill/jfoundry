<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" encoding="UTF-8" indent="no"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*[local-name()='profile'][*[local-name()='id'] = 'mason-central-poc']"/>

    <!-- Maven 4.1's super POM adds these filtered directories by default. -->
    <xsl:template match="*[local-name()='resources'][not(*[local-name()='resource'][not(*[local-name()='filtering'] = 'true' and contains(*[local-name()='directory'], '/src/main/resources-filtered'))])]"/>
    <xsl:template match="*[local-name()='testResources'][not(*[local-name()='testResource'][not(*[local-name()='filtering'] = 'true' and contains(*[local-name()='directory'], '/src/test/resources-filtered'))])]"/>
    <xsl:template match="*[local-name()='resource'][*[local-name()='filtering'] = 'true' and contains(*[local-name()='directory'], '/src/main/resources-filtered')]"/>
    <xsl:template match="*[local-name()='testResource'][*[local-name()='filtering'] = 'true' and contains(*[local-name()='directory'], '/src/test/resources-filtered')]"/>

    <!-- Maven 4.1 renames the reactor collection while preserving its meaning. -->
    <xsl:template match="*[local-name()='subprojects']">
        <xsl:element name="modules" namespace="{namespace-uri(.)}">
            <xsl:for-each select="*[local-name()='subproject']">
                <xsl:element name="module" namespace="{namespace-uri(.)}">
                    <xsl:value-of select="."/>
                </xsl:element>
            </xsl:for-each>
        </xsl:element>
    </xsl:template>

    <!-- Mason POC-only test handoff; the XML main baseline does not need it. -->
    <xsl:template match="*[local-name()='plugin'][*[local-name()='artifactId'] = 'quarkus-maven-plugin'][.//*[local-name()='goal'] = 'generate-code-tests']"/>
    <xsl:template match="*[local-name()='additionalClasspathDependencies']"/>
    <xsl:template match="*[local-name()='plugins'][not(*[local-name()='plugin'][not(*[local-name()='artifactId'] = 'quarkus-maven-plugin' and .//*[local-name()='goal'] = 'generate-code-tests')])]"/>
    <xsl:template match="*[local-name()='build'][not(*[not(local-name()='plugins')]) and not(*[local-name()='plugins']/*[local-name()='plugin'][not(*[local-name()='artifactId'] = 'quarkus-maven-plugin' and .//*[local-name()='goal'] = 'generate-code-tests')])]"/>
    <xsl:template match="*[local-name()='configuration'][not(*[not(local-name()='additionalClasspathDependencies')])]"/>
    <xsl:template match="*[local-name()='argLine'][contains(., '--add-opens=java.base') or contains(., '--add-exports=java.base')]"/>
    <xsl:template match="*[not(@*) and not(*) and not(normalize-space())]"/>

</xsl:stylesheet>
