-- Extiende sólo las categorías editoriales; no toca artículos, enlaces ni revisiones existentes.
ALTER TABLE knowledge_articles DROP CONSTRAINT knowledge_articles_category_check;
ALTER TABLE knowledge_articles ADD CONSTRAINT knowledge_articles_category_check
  CHECK (category IN ('INSTALLATION','CATALOGING','LOANS','REPORTS','IMPORT','OTHER',
                     'GUIDES','FAQ','SUPPORT','POLICIES_LICENSES','TRAINING','CONTACT'));
