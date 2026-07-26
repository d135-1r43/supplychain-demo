-- Seed data for dev and test mode. Loaded automatically by Hibernate ORM whenever the
-- schema is generated (see quarkus.hibernate-orm.database.generation in application.properties).
-- The speakers below are fictional.

insert into speaker (id, name, company, bio) values (1, 'Anna Bergmann', 'Nordlicht Software', 'Build engineer, spends most of her time explaining why the build is red.');
insert into speaker (id, name, company, bio) values (2, 'Tobias Reuter', 'Isarwerk GmbH', 'Backend developer with a weakness for dependency graphs.');
insert into speaker (id, name, company, bio) values (3, 'Meike Ostermann', 'Freelance', 'Security consultant, reformed Maven plugin author.');
alter sequence speaker_seq restart with 4;

insert into talk (id, title, summary, durationminutes, scheduledat, room, speaker_id) values (1, 'Was steckt eigentlich in meinem JAR?', 'A guided tour through the dependency tree of a boring REST service, and everything nobody declared on purpose.', 45, timestamp '2026-09-17 19:00:00', 'Hoersaal 1', 2);
insert into talk (id, title, summary, durationminutes, scheduledat, room, speaker_id) values (2, 'SBOMs in der Praxis', 'Generating CycloneDX documents in a Quarkus build and what the resulting file is actually good for.', 45, timestamp '2026-09-17 20:00:00', 'Hoersaal 1', 3);
insert into talk (id, title, summary, durationminutes, scheduledat, room, speaker_id) values (3, 'Dependency-Track im Betrieb', 'Running a vulnerability tracker for more than one project without drowning in findings.', 30, timestamp '2026-09-17 21:00:00', 'Hoersaal 2', 1);
insert into talk (id, title, summary, durationminutes, scheduledat, room, speaker_id) values (4, 'Der rote Build', 'On gating pipelines with policy violations, and the social consequences thereof.', 30, timestamp '2026-10-15 19:00:00', 'Hoersaal 2', 1);
alter sequence talk_seq restart with 5;
