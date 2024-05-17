/* H2 Style*/
--state: 0:pending 1:parsing 2:parsed 3:running 4:completed -1:failed -2:interrupted
create table manga_meta(
  id int generated always as identity,
  gallery_uri varchar(500) not null,
  title varchar(500) not null,
  total_pages int not null,
  completed_pages int not null,
  state smallint not null,
  cause varchar(500)
);


--status: 0:pending 1:running 2:completed -1:failed (-2:interrupted (deprecated))
create table manga_page(
  id int generated always as identity,
  page_uri varchar(500) not null,
  meta_id int not null,
  page_number int not null,
  path varchar(500) not null,
  state smallint not null
);


/*Sqlite Style
--state: 0:pending 1:parsing 2:parsed 3:running 4:completed -1:failed -2:interrupted
create table manga_meta(
  id rowid not null,
  gallery_uri text not null,
  title text not null,
  total_pages int not null,
  completed_pages int not null,
  state smallint not null,
  cause text
);


--status: 0:pending 1:running 2:completed -1:failed (-2:interrupted (deprecated))
create table manga_page(
  id rowid not null,
  page_uri text not null,
  meta_id integer not null,
  page_number integer not null,
  path text not null,
  state smallint not null
);
*/