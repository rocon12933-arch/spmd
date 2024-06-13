/* H2 Style*/
--state: 0:pending 1:running 2:completed -1:failed
create table manga_meta(
  id int generated always as identity,
  gallery_uri varchar(500) not null,
  is_parsed boolean not null default false,
  title varchar(500),
  total_pages int not null default 0,
  completed_pages int not null default 0,
  state smallint not null,
  cause varchar(500),
  created_at timestamp not null default CURRENT_TIMESTAMP
);


--status: 0:pending 1:running 2:completed -1:failed
create table manga_page(
  id int generated always as identity,
  page_uri varchar(500) not null,
  meta_id int not null,
  page_number int not null,
  title varchar(500) not null,
  state smallint not null,
  created_at timestamp not null default CURRENT_TIMESTAMP
);