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

    <!-- Mason POC-only test handoff; the XML main baseline does not need it. -->
    <xsl:template match="*[local-name()='plugin'][*[local-name()='artifactId'] = 'quarkus-maven-plugin'][.//*[local-name()='goal'] = 'generate-code-tests']"/>
    <xsl:template match="*[local-name()='additionalClasspathDependencies']"/>
    <xsl:template match="*[local-name()='plugins'][not(*[local-name()='plugin'][not(*[local-name()='artifactId'] = 'quarkus-maven-plugin' and .//*[local-name()='goal'] = 'generate-code-tests')])]"/>
    <xsl:template match="*[local-name()='build'][not(*[not(local-name()='plugins')]) and not(*[local-name()='plugins']/*[local-name()='plugin'][not(*[local-name()='artifactId'] = 'quarkus-maven-plugin' and .//*[local-name()='goal'] = 'generate-code-tests')])]"/>
    <xsl:template match="*[local-name()='configuration'][not(*[not(local-name()='additionalClasspathDependencies')])]"/>
    <xsl:template match="*[local-name()='argLine'][contains(., '--add-opens=java.base') or contains(., '--add-exports=java.base')]"/>
    <xsl:template match="*[not(@*) and not(*) and not(normalize-space())]"/>

</xsl:stylesheet>
