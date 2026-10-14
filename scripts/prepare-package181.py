#!/usr/bin/env python3
"""Prepara la distribución de código 1.8.1 y sus recursos de compilación."""
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]

def prepare():
    release = runpy.run_path(str(ROOT / 'scripts/release-1.8.1.py'))
    release['prepare']()
    pom = ROOT / 'backend/pom.xml'
    content = pom.read_text(encoding='utf-8')
    # Los recursos binarios no forman parte del ZIP de código. Maven obtiene
    # exactamente las mismas versiones del repositorio de origen, con SHA-256.
    if '<id>verified-pdf-resources</id>' not in content:
        plugin = '''<plugin>
      <groupId>org.apache.maven.plugins</groupId><artifactId>maven-antrun-plugin</artifactId><version>3.2.0</version>
      <executions><execution><id>verified-pdf-resources</id><phase>generate-resources</phase><goals><goal>run</goal></goals>
      <configuration><target>
        <mkdir dir="${project.build.outputDirectory}/fonts"/>
        <get src="https://raw.githubusercontent.com/rodolfo99/helpdesk-siabuc/b73bb839863641448f112c5285591f0503da4126/backend/src/main/resources/fonts/DejaVuSans.ttf" dest="${project.build.outputDirectory}/fonts/DejaVuSans.ttf" skipexisting="true" retries="3"/>
        <get src="https://raw.githubusercontent.com/rodolfo99/helpdesk-siabuc/b73bb839863641448f112c5285591f0503da4126/backend/src/main/resources/fonts/DejaVuSans-Bold.ttf" dest="${project.build.outputDirectory}/fonts/DejaVuSans-Bold.ttf" skipexisting="true" retries="3"/>
        <checksum file="${project.build.outputDirectory}/fonts/DejaVuSans.ttf" algorithm="SHA-256" property="ae7b7855e115a5966d8b1b3f80f254ccc117ec86f9965e202ee2940453837280" verifyproperty="pdf.regular.valid"/>
        <checksum file="${project.build.outputDirectory}/fonts/DejaVuSans-Bold.ttf" algorithm="SHA-256" property="5c1247acef7f2b8522a31742c76d6adcb5569bacc0be7ceaa4dc39dd252ce895" verifyproperty="pdf.bold.valid"/>
        <fail message="Recursos PDF corruptos: elimine target y repita la compilación."><condition><not><and><equals arg1="${pdf.regular.valid}" arg2="true"/><equals arg1="${pdf.bold.valid}" arg2="true"/></and></not></condition></fail>
      </target></configuration></execution></executions>
    </plugin>'''
        if '<plugins><plugin>' not in content:
            raise RuntimeError('Estructura POM no reconocida; no se modifica.')
        content = content.replace('<plugins><plugin>', '<plugins>' + plugin + '<plugin>', 1)
        pom.write_text(content, encoding='utf-8')
    for name in ['scripts/release-1.8.1.py', 'scripts/package-1.8.py']:
        p = ROOT / name
        text = p.read_text(encoding='utf-8')
        if "'.ttf'" not in text and '".ttf"' not in text:
            if name.endswith('release-1.8.1.py'):
                text = text.replace("'.zip', '.dump', '.pyc', '.pem', '.key', '.p12', '.pfx'", "'.zip', '.dump', '.pyc', '.pem', '.key', '.p12', '.pfx', '.ttf', '.otf', '.woff', '.woff2', '.ttc'")
            else:
                text = text.replace('".pyc", ".p12"', '".ttf", ".otf", ".woff", ".woff2", ".ttc", ".pyc", ".p12"')
            p.write_text(text, encoding='utf-8')
    # Validar la compilación tal como la recibirá quien descomprima el ZIP.
    for path in (ROOT / 'backend/src/main/resources/fonts').glob('*.ttf'):
        path.unlink()
    doc = ROOT / 'docs/VERSION-1.8.1.md'
    text = doc.read_text(encoding='utf-8')
    text += '\n## Recursos de compilación\n\nEl ZIP contiene código y licencias de recursos. Maven descarga los dos recursos\nPDF originales desde un commit fijo del mismo repositorio y verifica SHA-256.\nRequiere acceso HTTPS a raw.githubusercontent.com durante la compilación,\nademás de los repositorios Maven/npm. No sustituye la tipografía de los PDF.\n'
    doc.write_text(text, encoding='utf-8')
    print('Distribución 1.8.1 preparada; recursos externos fijados y verificados.')

if __name__ == '__main__':
    prepare()
