-- Index for public published-page lookup: site + path + status filter.
CREATE INDEX idx_pages_site_path_status ON pages(site_id, path, status);
