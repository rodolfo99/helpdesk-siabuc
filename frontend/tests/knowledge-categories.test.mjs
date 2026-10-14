import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {KNOWLEDGE_CATEGORIES, emptyKnowledge} from '../src/app/knowledge-models.ts';

const expected = ['INSTALLATION','CATALOGING','LOANS','REPORTS','IMPORT','OTHER',
  'GUIDES','FAQ','SUPPORT','POLICIES_LICENSES','TRAINING','CONTACT'];

test('conserva seis categorías originales y añade seis editoriales sin duplicados', () => {
  assert.deepEqual(KNOWLEDGE_CATEGORIES.map(item => item.value), expected);
  assert.equal(new Set(KNOWLEDGE_CATEGORIES.map(item => item.value)).size, 12);
});
test('las nuevas categorías tienen etiqueta legible en español', () => {
  const labels = Object.fromEntries(KNOWLEDGE_CATEGORIES.map(item => [item.value,item.label]));
  assert.equal(labels.GUIDES,'Guías y primeros pasos');
  assert.equal(labels.FAQ,'Preguntas frecuentes');
  assert.equal(labels.SUPPORT,'Solicitudes de soporte');
  assert.equal(labels.POLICIES_LICENSES,'Pólizas y licencias');
  assert.equal(labels.TRAINING,'Cursos y capacitación');
  assert.equal(labels.CONTACT,'Contacto y horarios');
});
test('los valores tienen etiqueta y el borrador conserva OTHER', () => {
  for (const item of KNOWLEDGE_CATEGORIES) {
    assert.ok(item.label.trim().length > 0);
    assert.notEqual(item.value,item.label);
  }
  assert.equal(emptyKnowledge().category,'OTHER');
});
test('soluciones usa categorías editoriales, no la ficha técnica del caso', () => {
  const source = readFileSync(new URL('../src/app/case-knowledge.component.ts',import.meta.url),'utf8');
  assert.doesNotMatch(source, /from ['"]\.\/case-profile\.component['"]/);
  assert.match(source, /readonly categories\s*=\s*KNOWLEDGE_CATEGORIES/);
  assert.match(source, /categoryLabel\(value:KnowledgeCategory\)/);
  assert.match(source, /category:KnowledgeCategory\|''/);
  assert.equal((source.match(/categoryLabel\((?:article|link\.article)\.category\)/g) ?? []).length,3);
});
