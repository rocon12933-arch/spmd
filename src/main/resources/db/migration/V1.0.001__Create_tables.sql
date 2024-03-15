/* H2 Style*/
--status: 0:pending 1:parsing 2:running 3:completed -1:interrupted -2:failed
create table manga_meta(
  id int generated always as identity,
  gallery_uri varchar(500) not null unique,
  title varchar(500) not null,
  total_pages int not null,
  completed_pages int not null,
  status smallint not null
);


--status: 0:pending 1:running 2:completed -1:failed
create table manga_page(
  id int generated always as identity,
  page_uri varchar(500) not null,
  meta_id int not null,
  page_number int not null,
  path varchar(500) not null,
  status smallint not null
);


/*Sqlite Style
--status: 0:pending 1:parsing 2:running 3:completed -1:interrupted -2:failed
create table manga_meta(
  id integer primary key,
  gallery_uri text not null unique,
  title text not null,
  total_pages int not null,
  completed_pages int not null,
  status smallint not null
);


--status: 0:pending 1:running 2:completed -1:failed
create table manga_page(
  id integer primary key,
  page_uri text not null,
  meta_id integer not null,
  page_number integer not null,
  path text not null,
  status smallint not null
);
*/