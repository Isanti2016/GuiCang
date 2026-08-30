-- V8 全文索引：file_index 增加内容列（md/txt 全文检索）
ALTER TABLE file_index ADD COLUMN content TEXT;
