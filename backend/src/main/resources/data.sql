
INSERT INTO school (school_name) VALUES
    ('Erik Jonsson School of Engineering and Computer Science'),
    ('School of Natural Sciences and Mathematics');

INSERT INTO department (school_id, department_name, department_code) VALUES
    (1, 'Computer Science', 'CS'),
    (2, 'Mathematics', 'MATH');

-- professor_rank is the primary key of this table
INSERT INTO evaluation_policy (professor_rank, evaluation_interval_years, active_status, made_date, effective_date, version) VALUES
    ('ASSISTANT', 1, TRUE, '2026-01-01', '2026-01-01', 0),
    ('ASSOCIATE', 2, TRUE, '2026-01-01', '2026-01-01', 0),
    ('FULL',      2, TRUE, '2026-01-01', '2026-01-01', 0);

-- Instructors of the courses below from Fall 2022 to Spring 2027, copied from the UTD Nebula API. Rank comes
-- from the Nebula title when it names a professor rank, otherwise it is NULL. Nebula has no hire dates.
-- Instructors who have not taught since Fall 2025 are inactive.
-- Names and emails are made up to keep the instructors anonymous; everything else is from Nebula.
INSERT INTO professor (department_id, professor_rank, first_name, last_name, email, hire_date, active_status) VALUES
    (1, 'ASSOCIATE', 'Adele',    'Amberly',      'adele.amberly@example.edu',      NULL, TRUE),
    (1, NULL,        'Ansel',    'Ashcombe',     'ansel.ashcombe@example.edu',     NULL, TRUE),
    (1, 'ASSISTANT', 'Beatrix',  'Bellwood',     'beatrix.bellwood@example.edu',   NULL, FALSE),
    (1, NULL,        'Cassius',  'Birchfield',   'cassius.birchfield@example.edu', NULL, TRUE),
    (1, 'FULL',      'Delphine', 'Blackmere',    'delphine.blackmere@example.edu', NULL, TRUE),
    (1, NULL,        'Emeric',   'Brambleton',   'emeric.brambleton@example.edu',  NULL, TRUE),
    (1, NULL,        'Fiona',    'Brightwater',  'fiona.brightwater@example.edu',  NULL, TRUE),
    (1, NULL,        'Gideon',   'Brookhollow',  'gideon.brookhollow@example.edu', NULL, FALSE),
    (1, 'ASSISTANT', 'Hollis',   'Carrowmore',   'hollis.carrowmore@example.edu',  NULL, TRUE),
    (1, 'ASSOCIATE', 'Isolde',   'Castellan',    'isolde.castellan@example.edu',   NULL, FALSE),
    (1, 'FULL',      'Jude',     'Cindervale',   'jude.cindervale@example.edu',    NULL, TRUE),
    (1, 'FULL',      'Kaia',     'Clearbrook',   'kaia.clearbrook@example.edu',    NULL, TRUE),
    (1, NULL,        'Leander',  'Coldwell',     'leander.coldwell@example.edu',   NULL, TRUE),
    (1, NULL,        'Marisol',  'Dalgrave',     'marisol.dalgrave@example.edu',   NULL, FALSE),
    (1, NULL,        'Nico',     'Deepwell',     'nico.deepwell@example.edu',      NULL, FALSE),
    (1, 'FULL',      'Odette',   'Dunmore',      'odette.dunmore@example.edu',     NULL, FALSE),
    (1, 'FULL',      'Perrin',   'Duskwood',     'perrin.duskwood@example.edu',    NULL, TRUE),
    (1, NULL,        'Rhea',     'Eastbrook',    'rhea.eastbrook@example.edu',     NULL, TRUE),
    (1, NULL,        'Silas',    'Elderidge',    'silas.elderidge@example.edu',    NULL, FALSE),
    (1, NULL,        'Thea',     'Emberton',     'thea.emberton@example.edu',      NULL, TRUE),
    (1, 'ASSOCIATE', 'Ulric',    'Fairhaven',    'ulric.fairhaven@example.edu',    NULL, TRUE),
    (1, NULL,        'Vivian',   'Fernsby',      'vivian.fernsby@example.edu',     NULL, TRUE),
    (1, NULL,        'Wesley',   'Foxhollow',    'wesley.foxhollow@example.edu',   NULL, FALSE),
    (1, NULL,        'Yara',     'Frostholm',    'yara.frostholm@example.edu',     NULL, FALSE),
    (1, 'ASSOCIATE', 'Zane',     'Glenhurst',    'zane.glenhurst@example.edu',     NULL, FALSE),
    (1, 'ASSOCIATE', 'Arlo',     'Glimmerton',   'arlo.glimmerton@example.edu',    NULL, TRUE),
    (1, 'FULL',      'Bianca',   'Goldmere',     'bianca.goldmere@example.edu',    NULL, TRUE),
    (1, NULL,        'Cyrus',    'Greystone',    'cyrus.greystone@example.edu',    NULL, TRUE),
    (1, 'ASSISTANT', 'Dahlia',   'Hallowell',    'dahlia.hallowell@example.edu',   NULL, FALSE),
    (1, NULL,        'Elio',     'Harrowgate',   'elio.harrowgate@example.edu',    NULL, TRUE),
    (1, NULL,        'Avery',    'Hazelwood',    'avery.hazelwood@example.edu',    NULL, TRUE),
    (1, 'FULL',      'Blake',    'Heathcote',    'blake.heathcote@example.edu',    NULL, FALSE),
    (1, NULL,        'Camille',  'Highmoor',     'camille.highmoor@example.edu',   NULL, FALSE),
    (1, NULL,        'Dario',    'Hollowell',    'dario.hollowell@example.edu',    NULL, TRUE),
    (1, NULL,        'Elena',    'Ironwood',     'elena.ironwood@example.edu',     NULL, TRUE),
    (1, NULL,        'Farid',    'Ivelmont',     'farid.ivelmont@example.edu',     NULL, FALSE),
    (1, 'ASSISTANT', 'Greta',    'Ivorydale',    'greta.ivorydale@example.edu',    NULL, TRUE),
    (1, 'FULL',      'Hiro',     'Juniper',      'hiro.juniper@example.edu',       NULL, TRUE),
    (1, 'ASSOCIATE', 'Imani',    'Kestrel',      'imani.kestrel@example.edu',      NULL, FALSE),
    (1, 'ASSOCIATE', 'Jonas',    'Lakeshore',    'jonas.lakeshore@example.edu',    NULL, TRUE),
    (1, 'ASSISTANT', 'Keira',    'Larkspur',     'keira.larkspur@example.edu',     NULL, TRUE),
    (1, NULL,        'Luca',     'Lindenhall',   'luca.lindenhall@example.edu',    NULL, TRUE),
    (1, NULL,        'Mira',     'Marchbank',    'mira.marchbank@example.edu',     NULL, TRUE),
    (1, NULL,        'Nikhil',   'Meadowcroft',  'nikhil.meadowcroft@example.edu', NULL, FALSE),
    (1, NULL,        'Orla',     'Merriweather', 'orla.merriweather@example.edu',  NULL, TRUE),
    (1, 'FULL',      'Pavel',    'Millbrook',    'pavel.millbrook@example.edu',    NULL, TRUE),
    (1, 'ASSOCIATE', 'Quinn',    'Moorfield',    'quinn.moorfield@example.edu',    NULL, TRUE),
    (1, NULL,        'Rosa',     'Northcott',    'rosa.northcott@example.edu',     NULL, FALSE),
    (1, NULL,        'Soren',    'Oakhurst',     'soren.oakhurst@example.edu',     NULL, TRUE),
    (1, 'ASSISTANT', 'Tamsin',   'Oldcastle',    'tamsin.oldcastle@example.edu',   NULL, TRUE),
    (1, NULL,        'Umar',     'Penhallow',    'umar.penhallow@example.edu',     NULL, FALSE),
    (1, NULL,        'Vera',     'Quarrington',  'vera.quarrington@example.edu',   NULL, TRUE),
    (1, NULL,        'Wren',     'Ravenhill',    'wren.ravenhill@example.edu',     NULL, TRUE),
    (1, NULL,        'Xiomara',  'Ridgeway',     'xiomara.ridgeway@example.edu',   NULL, TRUE),
    (1, NULL,        'Yusuf',    'Rookwood',     'yusuf.rookwood@example.edu',     NULL, TRUE),
    (1, NULL,        'Zara',     'Rosedale',     'zara.rosedale@example.edu',      NULL, FALSE),
    (1, 'ASSOCIATE', 'Anika',    'Saltmarsh',    'anika.saltmarsh@example.edu',    NULL, TRUE),
    (1, 'ASSISTANT', 'Bram',     'Silverton',    'bram.silverton@example.edu',     NULL, TRUE),
    (1, NULL,        'Celeste',  'Southwick',    'celeste.southwick@example.edu',  NULL, FALSE),
    (1, NULL,        'Devika',   'Stonebridge',  'devika.stonebridge@example.edu', NULL, TRUE),
    (1, 'ASSOCIATE', 'Esme',     'Thistlewood',  'esme.thistlewood@example.edu',   NULL, FALSE),
    (1, NULL,        'Felix',    'Thornbury',    'felix.thornbury@example.edu',    NULL, TRUE),
    (1, 'FULL',      'Gemma',    'Underhill',    'gemma.underhill@example.edu',    NULL, TRUE),
    (1, NULL,        'Hugo',     'Valemont',     'hugo.valemont@example.edu',      NULL, TRUE),
    (1, NULL,        'Ines',     'Westbrook',    'ines.westbrook@example.edu',     NULL, FALSE),
    (1, NULL,        'Jasper',   'Willowby',     'jasper.willowby@example.edu',    NULL, FALSE),
    (1, NULL,        'Kofi',     'Winterbourne', 'kofi.winterbourne@example.edu',  NULL, TRUE);

-- Semesters from the UTD Nebula API, Fall 2022 to Spring 2027, oldest first.
INSERT INTO semester (term, academic_year, start_date, end_date) VALUES
    ('Fall',   2022, '2022-08-22', '2022-12-16'),
    ('Spring', 2023, '2023-01-17', '2023-05-12'),
    ('Summer', 2023, '2023-05-24', '2023-08-10'),
    ('Fall',   2023, '2023-08-21', '2023-12-15'),
    ('Spring', 2024, '2024-01-16', '2024-05-10'),
    ('Summer', 2024, '2024-05-28', '2024-08-09'),
    ('Fall',   2024, '2024-08-19', '2024-12-13'),
    ('Spring', 2025, '2025-01-21', '2025-05-16'),
    ('Summer', 2025, '2025-06-02', '2025-08-15'),
    ('Fall',   2025, '2025-08-25', '2025-12-16'),
    ('Spring', 2026, '2026-01-20', '2026-05-15'),
    ('Summer', 2026, '2026-06-01', '2026-08-14'),
    ('Fall',   2026, '2026-08-24', '2026-12-16'),
    ('Spring', 2027, '2027-01-19', '2027-05-14');

-- CS courses copied from the UTD Nebula API (2026 catalog)
INSERT INTO course (department_id, course_code, course_number, course_name, course_level) VALUES
    (1, 'CS', '1337', 'Computer Science I', 'UNDERGRADUATE'),
    (1, 'CS', '2336', 'Computer Science II', 'UNDERGRADUATE'),
    (1, 'CS', '3345', 'Data Structures and Foundations of Algorithmic Analysis', 'UNDERGRADUATE'),
    (1, 'CS', '4485', 'Computer Science Project', 'UNDERGRADUATE'),
    (1, 'CS', '6363', 'Design and Analysis of Computer Algorithms', 'GRADUATE');

-- Class sections of the courses above from Fall 2022 to Spring 2027, copied from the UTD Nebula API.
-- Days use R for Thursday. Online sections without set times have NULL days and times.
INSERT INTO course_section (course_id, semester_id, section_number, meeting_days, start_time, end_time, location) VALUES
    -- Fall 2022 (semester 1)
    (1, 1, '0L1', 'TR', '13:00:00', '14:15:00', 'AD 2.232'),
    (1, 1, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.312'),
    (1, 1, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.412'),
    (1, 1, '503', 'MW', '17:30:00', '18:45:00', 'ECSS 2.415'),
    (2, 1, '001', 'MW', '14:30:00', '15:45:00', 'JO 3.516'),
    (2, 1, '002', 'TR', '08:30:00', '09:45:00', 'JSOM 1.212'),
    (2, 1, '003', 'MW', '10:00:00', '11:15:00', 'ECSW 1.365'),
    (2, 1, '004', 'MW', '08:30:00', '09:45:00', 'ECSS 2.305'),
    (2, 1, '005', 'MW', '11:30:00', '12:45:00', 'ECSS 2.311'),
    (2, 1, '006', 'TR', '14:30:00', '15:45:00', 'ML2 1.214'),
    (2, 1, '501', 'TR', '20:30:00', '21:45:00', 'ECSS 2.306'),
    (2, 1, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.305'),
    (2, 1, '503', 'TR', '19:00:00', '20:15:00', 'ECSW 3.210'),
    (2, 1, '504', 'TR', '17:30:00', '18:45:00', 'FO 3.616'),
    (3, 1, '001', 'MW', '13:00:00', '14:15:00', 'GR 4.301'),
    (3, 1, '002', 'MW', '16:00:00', '17:15:00', 'ECSS 2.305'),
    (3, 1, '003', 'MW', '14:30:00', '15:45:00', 'GR 2.302'),
    (3, 1, '004', 'MW', '08:30:00', '09:45:00', 'JSOM 12.206'),
    (3, 1, '005', 'TR', '08:30:00', '09:45:00', 'GR 3.302'),
    (3, 1, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.412'),
    (3, 1, '502', 'TR', '19:00:00', '20:15:00', 'ECSW 1.355'),
    (3, 1, '503', 'TR', '19:00:00', '20:15:00', 'ECSS 2.305'),
    (3, 1, '505', 'TR', '17:30:00', '18:45:00', 'ECSS 2.312'),
    (3, 1, 'HON', 'MW', '08:30:00', '09:45:00', 'ECSN 2.126'),
    (4, 1, '0W1', 'F', '13:00:00', '15:45:00', 'Online'),
    (5, 1, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.203'),
    (5, 1, '002', 'TR', '14:30:00', '15:45:00', 'ECSS 2.201'),
    (5, 1, '003', 'TR', '10:00:00', '11:15:00', 'JO 4.102'),
    (5, 1, '004', 'MW', '08:30:00', '09:45:00', 'ECSS 2.410'),
    (5, 1, '005', 'MW', '13:00:00', '14:15:00', 'AD 2.232'),
    (5, 1, '501', 'MW', '17:30:00', '18:45:00', 'CR 1.202'),
    -- Spring 2023 (semester 2)
    (1, 2, '001', 'MW', '14:30:00', '15:45:00', 'HH 2.502'),
    (1, 2, '002', 'MW', '10:00:00', '11:15:00', 'ECSS 2.311'),
    (1, 2, '003', 'MW', '10:00:00', '11:15:00', 'ECSS 2.410'),
    (1, 2, '004', 'MW', '11:30:00', '12:45:00', 'ECSS 2.312'),
    (1, 2, '005', 'MW', '08:30:00', '09:45:00', 'ECSS 2.305'),
    (1, 2, '006', 'MW', '16:00:00', '17:15:00', 'GR 3.420'),
    (1, 2, '007', 'TR', '13:00:00', '14:15:00', 'ECSS 2.312'),
    (1, 2, '008', 'TR', '14:30:00', '15:45:00', 'ECSS 2.312'),
    (1, 2, '009', 'MW', '11:30:00', '12:45:00', 'GR 2.530'),
    (1, 2, '010', 'MW', '11:30:00', '12:45:00', 'ECSS 2.201'),
    (1, 2, '011', 'MW', '16:00:00', '17:15:00', 'ECSS 2.305'),
    (1, 2, '012', 'MW', '14:30:00', '15:45:00', 'ECSS 2.412'),
    (1, 2, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.312'),
    (2, 2, '001', 'TR', '10:00:00', '11:15:00', 'JO 4.102'),
    (2, 2, '002', 'MW', '08:30:00', '09:45:00', 'JSOM 1.107'),
    (2, 2, '003', 'MW', '16:00:00', '17:15:00', 'ECSS 2.306'),
    (2, 2, '502', 'MW', '17:30:00', '18:45:00', 'MC 2.410'),
    (3, 2, '001', 'MW', '16:00:00', '17:15:00', 'MC 2.410'),
    (3, 2, '002', 'TR', '16:00:00', '17:15:00', 'ECSS 2.311'),
    (3, 2, '003', 'MW', '14:30:00', '15:45:00', 'ECSS 2.311'),
    (3, 2, '004', 'TR', '10:00:00', '11:15:00', 'CR 1.202'),
    (3, 2, '005', 'MW', '08:30:00', '09:45:00', 'GR 4.301'),
    (3, 2, '006', 'MW', '10:00:00', '11:15:00', 'ECSS 2.415'),
    (3, 2, '007', 'MW', '17:30:00', '18:45:00', 'ECSS 2.203'),
    (3, 2, '501', 'MW', '17:30:00', '18:45:00', 'GR 4.301'),
    (3, 2, '502', 'TR', '17:30:00', '18:45:00', 'GR 3.420'),
    (3, 2, '503', 'TR', '17:30:00', '18:45:00', 'GR 2.302'),
    (4, 2, '0W1', 'T', '13:00:00', '14:15:00', 'Online'),
    (5, 2, '001', 'TR', '10:00:00', '11:15:00', 'ECSS 2.312'),
    (5, 2, '003', 'TR', '13:00:00', '14:15:00', 'ECSS 2.203'),
    (5, 2, '004', 'MW', '16:00:00', '17:15:00', 'ECSS 2.415'),
    (5, 2, '501', 'MW', '17:30:00', '18:45:00', 'FN 2.102'),
    (5, 2, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.201'),
    -- Summer 2023 (semester 3)
    (1, 3, '0U1', 'MW', '12:30:00', '14:45:00', 'ECSN 2.120'),
    (1, 3, '5U1', 'TR', '17:30:00', '19:45:00', 'ECSS 2.311'),
    (2, 3, '0U1', 'MW', '12:30:00', '14:45:00', 'ECSS 2.201'),
    (2, 3, '5U1', 'TR', '17:30:00', '19:45:00', 'ECSS 2.203'),
    (3, 3, '0U1', 'TR', '15:00:00', '17:15:00', 'ECSS 2.306'),
    (5, 3, '0U1', 'TR', '10:00:00', '12:15:00', 'ECSS 2.203'),
    -- Fall 2023 (semester 4)
    (1, 4, '001', 'TR', '11:30:00', '12:45:00', 'ECSS 2.311'),
    (1, 4, '002', 'TR', '14:30:00', '15:45:00', 'ECSW 1.365'),
    (1, 4, '003', 'TR', '10:00:00', '11:15:00', 'ECSS 2.306'),
    (1, 4, '501', 'TR', '17:30:00', '18:45:00', 'ECSS 2.306'),
    (1, 4, '502', 'MW', '17:30:00', '18:45:00', 'ECSS 2.306'),
    (2, 4, '001', 'MW', '14:30:00', '15:45:00', 'HH 2.502'),
    (2, 4, '002', 'TR', '11:30:00', '12:45:00', 'HH 2.502'),
    (2, 4, '003', 'TR', '13:00:00', '14:15:00', 'HH 2.502'),
    (2, 4, '004', 'MW', '08:30:00', '09:45:00', 'GR 2.530'),
    (2, 4, '005', 'MW', '14:30:00', '15:45:00', 'GR 2.530'),
    (2, 4, '006', 'TR', '10:00:00', '11:15:00', 'GR 2.530'),
    (2, 4, '007', 'MW', '16:00:00', '17:15:00', 'GR 2.530'),
    (2, 4, '008', 'MW', '08:30:00', '09:45:00', 'JSOM 2.717'),
    (2, 4, '502', 'TR', '19:00:00', '20:15:00', 'HH 2.502'),
    (2, 4, '503', 'TR', '17:30:00', '18:45:00', 'GR 3.420'),
    (2, 4, '504', 'MW', '19:00:00', '20:15:00', 'GR 2.530'),
    (3, 4, '001', 'MW', '13:00:00', '14:15:00', 'ECSS 2.311'),
    (3, 4, '002', 'MW', '16:00:00', '17:15:00', 'ECSW 1.365'),
    (3, 4, '003', 'MW', '08:30:00', '09:45:00', 'ECSS 2.305'),
    (3, 4, '004', 'TR', '14:30:00', '15:45:00', 'ECSW 1.355'),
    (3, 4, '501', 'MW', '17:30:00', '18:45:00', 'ECSW 1.355'),
    (3, 4, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.412'),
    (3, 4, '503', 'TR', '19:00:00', '20:15:00', 'ECSS 2.306'),
    (3, 4, '504', 'TR', '17:30:00', '18:45:00', 'ECSW 1.355'),
    (3, 4, 'HON', 'TR', '10:00:00', '11:15:00', 'FN 2.302'),
    (4, 4, '0W1', 'M', '13:00:00', '14:15:00', 'Online'),
    (5, 4, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.203'),
    (5, 4, '002', 'TR', '14:30:00', '15:45:00', 'ECSS 2.203'),
    (5, 4, '003', 'TR', '10:00:00', '11:15:00', 'ECSS 2.201'),
    (5, 4, '004', 'MW', '14:30:00', '15:45:00', 'JO 4.102'),
    (5, 4, '005', 'MW', '13:00:00', '14:15:00', 'GR 2.530'),
    (5, 4, '501', 'MW', '17:30:00', '18:45:00', 'FN 2.214'),
    -- Spring 2024 (semester 5)
    (1, 5, '001', 'MW', '13:00:00', '14:15:00', 'JO 3.601'),
    (1, 5, '002', 'TR', '10:00:00', '11:15:00', 'GR 2.530'),
    (1, 5, '003', 'MW', '10:00:00', '11:15:00', 'ECSW 1.365'),
    (1, 5, '004', 'TR', '16:00:00', '17:15:00', 'JO 4.614'),
    (1, 5, '005', 'MW', '16:00:00', '17:15:00', 'ECSW 1.355'),
    (1, 5, '006', 'MW', '13:00:00', '14:15:00', 'ECSW 1.355'),
    (1, 5, '007', 'TR', '13:00:00', '14:15:00', 'GR 2.530'),
    (1, 5, '008', 'TR', '14:30:00', '15:45:00', 'HH 2.502'),
    (1, 5, '009', 'MW', '11:30:00', '12:45:00', 'HH 2.502'),
    (1, 5, '010', 'MW', '11:30:00', '12:45:00', 'ECSW 1.365'),
    (1, 5, '011', 'MW', '16:00:00', '17:15:00', 'GR 2.530'),
    (1, 5, '012', 'MW', '14:30:00', '15:45:00', 'ECSS 2.311'),
    (1, 5, '0W1', 'MW', '13:00:00', '14:15:00', 'Online'),
    (1, 5, '0W9', 'MW', '11:30:00', '12:45:00', 'Online'),
    (1, 5, '501', 'MW', '17:30:00', '18:45:00', 'GR 2.530'),
    (2, 5, '001', 'TR', '10:00:00', '11:15:00', 'ECSS 2.415'),
    (2, 5, '002', 'MW', '08:30:00', '09:45:00', 'JO 4.614'),
    (2, 5, '003', 'MW', '16:00:00', '17:15:00', 'HH 2.402'),
    (2, 5, '004', 'TR', '13:00:00', '14:15:00', 'CR 1.202'),
    (2, 5, '501', 'MW', '19:00:00', '20:15:00', 'MC 2.410'),
    (3, 5, '001', 'MW', '16:00:00', '17:15:00', 'ECSW 1.365'),
    (3, 5, '002', 'TR', '16:00:00', '17:15:00', 'ECSW 1.355'),
    (3, 5, '003', 'MW', '14:30:00', '15:45:00', 'ECSS 2.305'),
    (3, 5, '004', 'TR', '10:00:00', '11:15:00', 'ECSS 2.203'),
    (3, 5, '005', 'MW', '08:30:00', '09:45:00', 'ECSS 2.306'),
    (3, 5, '006', 'MW', '10:00:00', '11:15:00', 'ECSW 1.355'),
    (3, 5, '007', 'MW', '11:30:00', '12:45:00', 'ECSW 1.355'),
    (3, 5, '008', 'MW', '10:00:00', '11:15:00', 'JO 3.601'),
    (3, 5, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.306'),
    (3, 5, '502', 'TR', '17:30:00', '18:45:00', 'ECSW 1.355'),
    (3, 5, '503', 'TR', '17:30:00', '18:45:00', 'GR 2.302'),
    (4, 5, '0W1', 'T', '13:00:00', '14:15:00', 'Online'),
    (5, 5, '001', 'TR', '10:00:00', '11:15:00', 'ECSS 2.311'),
    (5, 5, '002', 'TR', '13:00:00', '14:15:00', 'ECSS 2.203'),
    (5, 5, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.203'),
    (5, 5, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.203'),
    -- Summer 2024 (semester 6)
    (1, 6, '0U1', 'MW', '12:30:00', '14:45:00', 'ECSN 2.120'),
    (1, 6, '5W1', 'TR', '17:30:00', '19:45:00', 'Online'),
    (2, 6, '5U1', 'TR', '17:30:00', '19:45:00', 'ECSS 2.203'),
    (3, 6, '0U1', 'TR', '15:00:00', '17:15:00', 'ECSS 2.306'),
    (4, 6, '0W1', 'MF', '13:00:00', '15:15:00', 'Online'),
    -- Fall 2024 (semester 7)
    (1, 7, '001', 'TR', '11:30:00', '12:45:00', 'JO 4.102'),
    (1, 7, '002', 'TR', '14:30:00', '15:45:00', 'ECSW 1.355'),
    (1, 7, '004', 'MW', '16:00:00', '17:15:00', 'ECSW 1.355'),
    (1, 7, '5W1', 'MW', '17:30:00', '18:45:00', 'Online'),
    (2, 7, '001', 'MW', '13:00:00', '14:15:00', 'ECSW 3.250'),
    (2, 7, '002', 'TR', '11:30:00', '12:45:00', 'ECSW 3.250'),
    (2, 7, '003', 'TR', '13:00:00', '14:15:00', 'ECSW 1.365'),
    (2, 7, '004', 'MW', '11:30:00', '12:45:00', 'ECSW 3.250'),
    (2, 7, '005', 'MW', '14:30:00', '15:45:00', 'ECSW 3.210'),
    (2, 7, '006', 'TR', '10:00:00', '11:15:00', 'ECSW 3.250'),
    (2, 7, '007', 'MW', '16:00:00', '17:15:00', 'GR 2.302'),
    (2, 7, '008', 'MW', '10:00:00', '11:15:00', 'ECSW 3.250'),
    (2, 7, '009', 'TR', '14:30:00', '15:45:00', 'ECSW 3.250'),
    (2, 7, '501', 'MW', '17:30:00', '18:45:00', 'JO 3.601'),
    (2, 7, '502', 'TR', '19:00:00', '20:15:00', 'ECSW 3.250'),
    (2, 7, '503', 'TR', '17:30:00', '18:45:00', 'JO 3.601'),
    (2, 7, '504', 'MW', '19:00:00', '20:15:00', 'ECSW 3.250'),
    (3, 7, '001', 'MW', '13:00:00', '14:15:00', 'GR 3.302'),
    (3, 7, '002', 'MW', '16:00:00', '17:15:00', 'JO 3.601'),
    (3, 7, '003', 'MW', '08:30:00', '09:45:00', 'ECSW 1.365'),
    (3, 7, '004', 'TR', '14:30:00', '15:45:00', 'ECSW 1.365'),
    (3, 7, '005', 'MW', '14:30:00', '15:45:00', 'ECSW 1.355'),
    (3, 7, '006', 'TR', '10:00:00', '11:15:00', 'ECSS 2.305'),
    (3, 7, '501', 'MW', '17:30:00', '18:45:00', 'JO 4.102'),
    (3, 7, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.412'),
    (3, 7, '503', 'TR', '19:00:00', '20:15:00', 'ECSS 2.306'),
    (3, 7, 'HON', 'TR', '10:00:00', '11:15:00', 'AD 2.216'),
    (4, 7, '0W1', 'M', '13:00:00', '14:15:00', 'Online'),
    (4, 7, '0W2', NULL, NULL, NULL, 'Online'),
    (5, 7, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.312'),
    (5, 7, '002', 'MW', '14:30:00', '15:45:00', 'ECSS 2.312'),
    (5, 7, '003', 'TR', '10:00:00', '11:15:00', 'ECSS 2.312'),
    -- Spring 2025 (semester 8)
    (1, 8, '001', 'TR', '16:00:00', '17:15:00', 'ECSS 2.412'),
    (1, 8, '002', 'MW', '10:00:00', '11:15:00', 'ECSW 1.365'),
    (1, 8, '003', 'MW', '10:00:00', '11:15:00', 'ECSW 1.355'),
    (1, 8, '004', 'TR', '16:00:00', '17:15:00', 'SLC 2.303'),
    (1, 8, '005', 'MW', '11:30:00', '12:45:00', 'ECSW 1.365'),
    (1, 8, '006', 'MW', '11:30:00', '12:45:00', 'ECSS 2.311'),
    (1, 8, '007', 'TR', '13:00:00', '14:15:00', 'ECSS 2.305'),
    (1, 8, '008', 'TR', '13:00:00', '14:15:00', 'ECSS 2.306'),
    (1, 8, '009', 'TR', '13:00:00', '14:15:00', 'ECSW 1.355'),
    (1, 8, '010', 'TR', '16:00:00', '17:15:00', 'FN 2.102'),
    (1, 8, '0W1', 'MW', '10:00:00', '11:15:00', 'Online'),
    (1, 8, '0W2', 'MW', '11:30:00', '12:45:00', 'Online'),
    (2, 8, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.415'),
    (2, 8, '003', 'MW', '11:30:00', '12:45:00', 'GR 2.530'),
    (2, 8, '005', 'TR', '11:30:00', '12:45:00', 'ECSS 2.201'),
    (2, 8, '007', 'TR', '14:30:00', '15:45:00', 'ECSS 2.201'),
    (2, 8, '008', 'TR', '14:30:00', '15:45:00', 'ECSS 2.203'),
    (2, 8, '501', 'TR', '17:30:00', '18:45:00', 'ECSS 2.306'),
    (3, 8, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.412'),
    (3, 8, '002', 'TR', '13:00:00', '14:15:00', 'ECSS 2.311'),
    (3, 8, '003', 'MW', '10:00:00', '11:15:00', 'ECSS 2.306'),
    (3, 8, '005', 'MW', '08:30:00', '09:45:00', 'ECSS 2.305'),
    (3, 8, '006', 'MW', '08:30:00', '09:45:00', 'ECSS 2.306'),
    (3, 8, '007', 'MW', '10:00:00', '11:15:00', 'JO 3.601'),
    (3, 8, '008', 'MW', '08:30:00', '09:45:00', 'ECSS 2.412'),
    (3, 8, '009', 'TR', '13:00:00', '14:15:00', 'ECSW 1.365'),
    (3, 8, '501', 'TR', '17:30:00', '18:45:00', 'ECSS 2.305'),
    (3, 8, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.410'),
    (3, 8, '504', 'MW', '17:30:00', '18:45:00', 'ECSS 2.415'),
    (4, 8, '0W1', 'T', '13:00:00', '14:15:00', 'Online'),
    (5, 8, '001', 'TR', '10:00:00', '11:15:00', 'ECSS 2.311'),
    (5, 8, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.203'),
    (5, 8, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.203'),
    -- Summer 2025 (semester 9)
    (1, 9, '0U2', 'MW', '12:30:00', '14:30:00', 'SCI 3.240'),
    (2, 9, '5U1', 'TR', '17:30:00', '19:45:00', 'JSOM 2.102'),
    (2, 9, '5U2', 'TR', '17:30:00', '19:45:00', 'ATC 2.101'),
    (3, 9, '0U1', 'TR', '15:00:00', '17:15:00', 'JSOM 2.714'),
    (4, 9, '0W1', 'MWF', '13:00:00', '15:00:00', 'Online'),
    -- Fall 2025 (semester 10)
    (1, 10, '001', 'MW', '14:30:00', '15:45:00', 'ECSW 3.210'),
    (1, 10, '002', 'MW', '14:30:00', '15:45:00', 'ECSW 3.250'),
    (1, 10, '003', 'TR', '08:30:00', '09:45:00', 'JSOM 2.714'),
    (1, 10, '004', 'TR', '10:00:00', '11:15:00', 'ECSS 2.201'),
    (2, 10, '001', 'MW', '08:30:00', '09:45:00', 'ECSW 1.355'),
    (2, 10, '002', 'MW', '10:00:00', '11:15:00', 'JO 4.102'),
    (2, 10, '003', 'MW', '10:00:00', '11:15:00', 'ECSW 1.355'),
    (2, 10, '004', 'MW', '11:30:00', '12:45:00', 'JO 4.102'),
    (2, 10, '005', 'MW', '11:30:00', '12:45:00', 'ECSW 1.355'),
    (2, 10, '006', 'MW', '11:30:00', '12:45:00', 'JO 3.601'),
    (2, 10, '007', 'TR', '11:30:00', '12:45:00', 'ECSW 1.365'),
    (2, 10, '008', 'TR', '11:30:00', '12:45:00', 'JO 4.102'),
    (2, 10, '009', 'TR', '11:30:00', '12:45:00', 'ECSS 2.203'),
    (2, 10, '010', 'TR', '14:30:00', '15:45:00', 'GR 3.302'),
    (2, 10, '011', 'TR', '14:30:00', '15:45:00', 'GR 3.420'),
    (2, 10, '012', 'TR', '14:30:00', '15:45:00', 'ECSW 1.365'),
    (3, 10, '001', 'MW', '08:30:00', '09:45:00', 'ECSS 2.410'),
    (3, 10, '002', 'MW', '08:30:00', '09:45:00', 'JO 4.102'),
    (3, 10, '003', 'MW', '10:00:00', '11:15:00', 'ECSS 2.410'),
    (3, 10, '005', 'TR', '13:00:00', '14:15:00', 'ECSW 1.355'),
    (3, 10, '006', 'TR', '13:00:00', '14:15:00', 'ECSS 2.415'),
    (3, 10, '007', 'TR', '08:30:00', '09:45:00', 'SOM 1.102'),
    (3, 10, '008', 'TR', '13:00:00', '14:15:00', 'GR 2.530'),
    (3, 10, '501', 'TR', '17:30:00', '18:45:00', 'JO 3.601'),
    (3, 10, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.412'),
    (3, 10, '503', 'MW', '17:30:00', '18:45:00', 'JO 4.102'),
    (3, 10, 'HON', 'TR', '10:00:00', '11:15:00', 'AD 2.232'),
    (4, 10, '0W1', 'T', '13:00:00', '14:15:00', 'Online'),
    (5, 10, '002', 'TR', '08:30:00', '09:45:00', 'ECSW 1.355'),
    (5, 10, '003', 'TR', '13:00:00', '14:15:00', 'JO 4.102'),
    -- Spring 2026 (semester 11)
    (1, 11, '001', 'TR', '16:00:00', '17:15:00', 'ECSW 1.365'),
    (1, 11, '002', 'MW', '10:00:00', '11:15:00', 'ECSS 2.203'),
    (1, 11, '003', 'MW', '10:00:00', '11:15:00', 'ECSW 1.355'),
    (1, 11, '004', 'TR', '16:00:00', '17:15:00', 'GR 2.302'),
    (1, 11, '005', 'MW', '11:30:00', '12:45:00', 'ECSS 2.203'),
    (1, 11, '006', 'MW', '11:30:00', '12:45:00', 'JO 3.601'),
    (1, 11, '007', 'TR', '13:00:00', '14:15:00', 'ECSS 2.306'),
    (1, 11, '008', 'TR', '13:00:00', '14:15:00', 'ECSS 2.305'),
    (1, 11, '009', 'TR', '13:00:00', '14:15:00', 'GR 2.302'),
    (1, 11, '010', 'TR', '16:00:00', '17:15:00', 'JO 4.614'),
    (1, 11, '011', 'MW', '10:00:00', '11:15:00', 'GR 2.302'),
    (1, 11, '501', 'TR', '17:30:00', '18:45:00', 'ECSW 1.365'),
    (2, 11, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.201'),
    (2, 11, '003', 'MW', '11:30:00', '12:45:00', 'GR 2.530'),
    (2, 11, '005', 'TR', '11:30:00', '12:45:00', 'JO 4.102'),
    (2, 11, '007', 'TR', '14:30:00', '15:45:00', 'ECSS 2.201'),
    (2, 11, '501', 'TR', '17:30:00', '18:45:00', 'CR 1.202'),
    (3, 11, '001', 'MW', '10:00:00', '11:15:00', 'JO 4.614'),
    (3, 11, '002', 'TR', '13:00:00', '14:15:00', 'JO 3.601'),
    (3, 11, '003', 'MW', '10:00:00', '11:15:00', 'ECSW 1.365'),
    (3, 11, '004', 'TR', '13:00:00', '14:15:00', 'ECSW 1.365'),
    (3, 11, '005', 'MW', '08:30:00', '09:45:00', 'ECSW 1.355'),
    (3, 11, '007', 'MW', '10:00:00', '11:15:00', 'ECSS 2.306'),
    (3, 11, '008', 'MW', '08:30:00', '09:45:00', 'ECSS 2.306'),
    (3, 11, '501', 'TR', '17:30:00', '18:45:00', 'ECSS 2.306'),
    (3, 11, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.305'),
    (3, 11, '503', 'MW', '17:30:00', '18:45:00', 'JO 4.102'),
    (3, 11, '504', 'MW', '17:30:00', '18:45:00', 'ECSS 2.305'),
    (4, 11, '0W1', 'T', '14:30:00', '17:15:00', 'Online'),
    (5, 11, '001', 'TR', '10:00:00', '11:15:00', 'ECSS 2.311'),
    (5, 11, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.203'),
    -- Summer 2026 (semester 12)
    (1, 12, '0W1', 'MW', '12:30:00', '14:30:00', 'Online'),
    (2, 12, '0U1', 'TR', '15:00:00', '17:15:00', 'JSOM 2.102'),
    (2, 12, '5U1', 'TR', '17:30:00', '19:45:00', 'JSOM 2.102'),
    (3, 12, '0U1', 'TR', '15:00:00', '17:15:00', 'ECSS 2.201'),
    (3, 12, '5W1', 'MW', '17:30:00', '19:45:00', 'Online'),
    (4, 12, '0W1', 'MWF', '13:00:00', '15:00:00', 'Online'),
    -- Fall 2026 (semester 13)
    (1, 13, '001', 'MW', '14:30:00', '15:45:00', 'ECSW 3.210'),
    (1, 13, '002', 'MW', '14:30:00', '15:45:00', 'ECSW 3.250'),
    (1, 13, '004', 'TR', '10:00:00', '11:15:00', 'ECSS 2.201'),
    (2, 13, '002', 'MW', '10:00:00', '11:15:00', 'ECSW 1.355'),
    (2, 13, '003', 'MW', '10:00:00', '11:15:00', 'JO 4.102'),
    (2, 13, '004', 'MW', '11:30:00', '12:45:00', 'JO 4.102'),
    (2, 13, '006', 'MW', '08:30:00', '09:45:00', 'GR 4.301'),
    (2, 13, '007', 'TR', '11:30:00', '12:45:00', 'ECSW 1.365'),
    (2, 13, '008', 'TR', '11:30:00', '12:45:00', 'JO 4.102'),
    (2, 13, '009', 'TR', '11:30:00', '12:45:00', 'ECSS 2.203'),
    (2, 13, '010', 'TR', '14:30:00', '15:45:00', 'ECSS 2.305'),
    (2, 13, '011', 'MW', '14:30:00', '15:45:00', 'FN 2.214'),
    (2, 13, '012', 'TR', '14:30:00', '15:45:00', 'ECSW 1.365'),
    (3, 13, '001', 'MW', '08:30:00', '09:45:00', 'ECSS 2.203'),
    (3, 13, '002', 'MW', '08:30:00', '09:45:00', 'JO 4.102'),
    (3, 13, '003', 'MW', '10:00:00', '11:15:00', 'HH 2.502'),
    (3, 13, '004', 'TR', '13:00:00', '14:15:00', 'ECSS 2.415'),
    (3, 13, '005', 'TR', '13:00:00', '14:15:00', 'ECSW 1.355'),
    (3, 13, '008', 'TR', '13:00:00', '14:15:00', 'CR 1.202'),
    (3, 13, '501', 'TR', '17:30:00', '18:45:00', 'ECSS 2.305'),
    (3, 13, '502', 'TR', '17:30:00', '18:45:00', 'JO 4.102'),
    (3, 13, 'HON', 'TR', '10:00:00', '11:15:00', 'FO 2.404'),
    (4, 13, '0W1', 'T', '13:00:00', '14:15:00', 'Online'),
    (5, 13, '002', 'MW', '14:30:00', '15:45:00', 'JO 4.102'),
    (5, 13, '003', 'TR', '13:00:00', '14:15:00', 'JO 4.102'),
    -- Spring 2027 (semester 14)
    (1, 14, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.203'),
    (1, 14, '002', 'TR', '16:00:00', '17:15:00', 'FO 2.404'),
    (1, 14, '003', 'TR', '16:00:00', '17:15:00', 'ECSS 2.410'),
    (1, 14, '004', 'MW', '11:30:00', '12:45:00', 'ECSS 2.203'),
    (1, 14, '005', 'MW', '11:30:00', '12:45:00', NULL),
    (1, 14, '006', 'TR', '13:00:00', '14:15:00', 'ECSS 2.306'),
    (1, 14, '007', 'MW', '10:00:00', '11:15:00', 'GR 2.302'),
    (1, 14, '501', 'TR', '17:30:00', '18:45:00', 'GR 2.302'),
    (1, 14, '502', 'MW', '17:30:00', '18:45:00', 'GR 2.302'),
    (1, 14, '503', 'MW', '17:30:00', '18:45:00', 'ECSW 1.355'),
    (2, 14, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.201'),
    (2, 14, '002', 'TR', '14:30:00', '15:45:00', 'ECSS 2.201'),
    (2, 14, '003', 'MW', '11:30:00', '12:45:00', 'CR 1.202'),
    (2, 14, '004', 'TR', '11:30:00', '12:45:00', 'JO 4.102'),
    (2, 14, '501', 'TR', '17:30:00', '18:45:00', 'FO 2.404'),
    (3, 14, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.305'),
    (3, 14, '002', 'TR', '13:00:00', '14:15:00', 'ECSW 1.355'),
    (3, 14, '003', 'MW', '10:00:00', '11:15:00', 'ECSW 1.365'),
    (3, 14, '004', 'TR', '13:00:00', '14:15:00', 'ECSW 1.365'),
    (3, 14, '005', 'MW', '08:30:00', '09:45:00', 'ECSW 1.355'),
    (3, 14, '006', 'MW', '08:30:00', '09:45:00', 'ECSS 2.306'),
    (3, 14, '007', 'MW', '10:00:00', '11:15:00', 'ECSS 2.306'),
    (3, 14, '501', 'TR', '17:30:00', '18:45:00', 'ECSS 2.306'),
    (3, 14, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.305'),
    (3, 14, '503', 'MW', '17:30:00', '18:45:00', 'JO 4.102'),
    (3, 14, '504', 'MW', '17:30:00', '18:45:00', 'ECSS 2.305'),
    (4, 14, '0W1', 'T', '14:30:00', '17:15:00', 'Online'),
    (5, 14, '001', 'TR', '10:00:00', '11:15:00', 'ECSS 2.311'),
    (5, 14, '501', 'TR', '17:30:00', '18:45:00', 'ECSS 2.203');

-- Which professor taught which section, from the UTD Nebula API. One line per professor.
INSERT INTO teaching_history (professor_id, section_id) VALUES
    (1, 59), (1, 90), (1, 94), (1, 134), (1, 164), (1, 166), (1, 196), (1, 205), (1, 263), (1, 267), (1, 272), (1, 323), (1, 325), (1, 332), -- Adele Amberly
    (2, 48), (2, 151), (2, 153), (2, 181), (2, 214), (2, 249), (2, 254), (2, 290), (2, 293), -- Ansel Ashcombe
    (3, 3), (3, 134), (3, 205), -- Beatrix Bellwood
    (4, 13), (4, 14), (4, 55), (4, 269), -- Cassius Birchfield
    (5, 31), (5, 63), (5, 102), (5, 126), (5, 137), (5, 207), (5, 324), -- Delphine Blackmere
    (6, 216), -- Emeric Brambleton
    (7, 5), (7, 48), (7, 76), (7, 218), (7, 228), -- Fiona Brightwater
    (8, 134), -- Gideon Brookhollow
    (9, 161), (9, 169), (9, 198), (9, 211), (9, 226), (9, 229), (9, 276), (9, 284), (9, 288), (9, 291), (9, 317), (9, 319), -- Hollis Carrowmore
    (10, 10), (10, 83), (10, 96), -- Isolde Castellan
    (11, 29), (11, 70), (11, 97), (11, 98), (11, 129), (11, 173), (11, 242), -- Jude Cindervale
    (12, 59), (12, 96), (12, 134), (12, 171), (12, 205), (12, 241), (12, 272), (12, 303), (12, 332), -- Kaia Clearbrook
    (13, 178), (13, 200), (13, 225), (13, 230), (13, 232), (13, 235), (13, 270), (13, 277), (13, 278), (13, 294), (13, 310), (13, 311), (13, 328), (13, 330), -- Leander Coldwell
    (14, 71), -- Marisol Dalgrave
    (15, 82), (15, 136), -- Nico Deepwell
    (16, 41), (16, 43), (16, 112), (16, 114), -- Odette Dunmore
    (17, 194), (17, 233), (17, 261), (17, 299), (17, 321), -- Perrin Duskwood
    (18, 272), -- Rhea Eastbrook
    (19, 32), (19, 40), (19, 65), (19, 75), (19, 103), (19, 111), (19, 115), (19, 116), (19, 140), (19, 147), (19, 186), (19, 187), -- Silas Elderidge
    (20, 64), (20, 138), (20, 208), (20, 274), (20, 334), -- Thea Emberton
    (21, 27), (21, 62), (21, 130), (21, 165), (21, 326), -- Ulric Fairhaven
    (22, 53), (22, 54), (22, 77), (22, 79), (22, 80), (22, 81), (22, 127), (22, 128), (22, 149), (22, 220), (22, 222), (22, 275), (22, 287), -- Vivian Fernsby
    (23, 24), (23, 61), (23, 95), (23, 170), -- Wesley Foxhollow
    (24, 171), -- Yara Frostholm
    (25, 66), (25, 85), (25, 106), (25, 121), (25, 139), (25, 209), -- Zane Glenhurst
    (26, 16), (26, 18), (26, 58), (26, 88), (26, 89), (26, 133), (26, 162), (26, 197), (26, 202), (26, 231), (26, 237), (26, 265), (26, 268), (26, 295), (26, 300), (26, 329), (26, 331), -- Arlo Glimmerton
    (27, 28), (27, 60), (27, 99), (27, 135), (27, 175), (27, 206), (27, 243), (27, 273), (27, 305), (27, 333), -- Bianca Goldmere
    (28, 205), (28, 241), (28, 272), (28, 303), -- Cyrus Greystone
    (29, 17), -- Dahlia Hallowell
    (30, 179), (30, 184), (30, 247), (30, 252), (30, 308), -- Elio Harrowgate
    (31, 12), (31, 20), (31, 21), (31, 56), (31, 57), (31, 68), (31, 86), (31, 91), (31, 92), (31, 131), (31, 132), (31, 141), (31, 158), (31, 160), (31, 167), (31, 168), (31, 203), (31, 204), (31, 210), (31, 238), (31, 239), (31, 271), (31, 301), -- Avery Hazelwood
    (32, 96), (32, 134), -- Blake Heathcote
    (33, 2), (33, 35), (33, 42), (33, 44), (33, 74), -- Camille Highmoor
    (34, 298), -- Dario Hollowell
    (35, 236), (35, 258), (35, 259), -- Elena Ironwood
    (36, 122), -- Farid Ivelmont
    (37, 67), (37, 303), -- Greta Ivorydale
    (38, 249), (38, 254), -- Hiro Juniper
    (39, 59), -- Imani Kestrel
    (40, 73), (40, 104), (40, 176), (40, 303), (40, 320), -- Jonas Lakeshore
    (41, 163), (41, 240), (41, 302), -- Keira Larkspur
    (42, 134), (42, 241), -- Luca Lindenhall
    (43, 241), (43, 244), (43, 255), (43, 313), -- Mira Marchbank
    (44, 15), (44, 22), (44, 49), (44, 87), (44, 93), (44, 123), -- Nikhil Meadowcroft
    (45, 84), (45, 148), (45, 155), (45, 285), -- Orla Merriweather
    (46, 241), -- Pavel Millbrook
    (47, 26), (47, 30), (47, 100), (47, 101), (47, 174), (47, 304), -- Quinn Moorfield
    (48, 11), (48, 154), -- Rosa Northcott
    (49, 4), (49, 25), (49, 33), (49, 36), (49, 59), (49, 96), (49, 113), (49, 117), (49, 134), (49, 143), (49, 144), (49, 171), (49, 172), (49, 205), (49, 213), (49, 215), (49, 217), (49, 241), (49, 244), (49, 250), (49, 255), (49, 272), (49, 280), (49, 282), (49, 303), (49, 312), (49, 313), (49, 332), -- Soren Oakhurst
    (50, 6), (50, 46), (50, 47), (50, 119), (50, 120), (50, 188), (50, 189), (50, 256), (50, 257), (50, 316), (50, 318), -- Tamsin Oldcastle
    (51, 171), (51, 205), -- Umar Penhallow
    (52, 272), -- Vera Quarrington
    (53, 205), (53, 241), (53, 272), -- Wren Ravenhill
    (54, 51), (54, 125), (54, 156), (54, 195), (54, 199), (54, 219), (54, 221), (54, 234), (54, 262), (54, 266), (54, 286), (54, 289), (54, 296), (54, 297), (54, 322), (54, 327), -- Xiomara Ridgeway
    (55, 279), (55, 303), -- Yusuf Rookwood
    (56, 159), (56, 193), -- Zara Rosedale
    (57, 1), (57, 7), (57, 38), (57, 39), (57, 45), (57, 72), (57, 78), (57, 109), (57, 110), (57, 118), (57, 145), (57, 150), (57, 182), (57, 185), (57, 190), (57, 224), (57, 227), (57, 246), -- Anika Saltmarsh
    (58, 34), (58, 105), (58, 107), (58, 108), (58, 146), (58, 177), (58, 180), (58, 245), (58, 248), (58, 281), (58, 283), (58, 306), (58, 309), (58, 315), -- Bram Silverton
    (59, 171), (59, 183), (59, 192), (59, 205), -- Celeste Southwick
    (60, 157), (60, 260), -- Devika Stonebridge
    (61, 59), (61, 134), -- Esme Thistlewood
    (62, 264), -- Felix Thornbury
    (63, 8), (63, 9), (63, 37), (63, 152), (63, 191), (63, 223), (63, 249), (63, 254), (63, 292), (63, 332), -- Gemma Underhill
    (64, 251), (64, 253), (64, 314), -- Hugo Valemont
    (65, 59), -- Ines Westbrook
    (66, 19), (66, 23), (66, 50), (66, 52), (66, 69), (66, 124), (66, 142), (66, 201), (66, 212), -- Jasper Willowby
    (67, 303); -- Kofi Winterbourne

-- Demo data, which the backend tests also run against. Everything from here down is made up, including the ranks,
-- hire dates and evaluation history the Nebula data does not have, so the evaluation features have something to
-- show. Rows are looked up by email and course number rather than by generated id. Changing them can change what
-- the tests expect.

-- Fall 2026 is the current cycle. It is inserted first, so it is evaluation cycle 1.
INSERT INTO evaluation_cycle (semester_id, signup_deadline, observation_deadline, feedback_deadline, status)
SELECT semester_id, DATE '2026-10-31', DATE '2026-11-15', DATE '2026-12-01', 'OPEN'
FROM semester WHERE term = 'Fall' AND academic_year = 2026;
CREATE LOCAL TEMPORARY TABLE demo_cycle AS
SELECT c.cycle_id, c.semester_id FROM evaluation_cycle c JOIN semester s ON s.semester_id = c.semester_id
WHERE s.term = 'Fall' AND s.academic_year = 2026;

-- Later semesters, so every upcoming evaluation date falls in a named semester.
INSERT INTO semester (term, academic_year, start_date, end_date) VALUES
    ('Summer', 2027, '2027-05-26', '2027-08-12'),
    ('Fall',   2027, '2027-08-23', '2027-12-15'),
    ('Spring', 2028, '2028-01-18', '2028-05-12');

-- A Mathematics professor, in the other school, so committee access stops at the school boundary.
INSERT INTO professor (department_id, professor_rank, first_name, last_name, email, hire_date, active_status)
SELECT department_id, 'ASSOCIATE', 'Isla', 'Wrenfield', 'isla.wrenfield@example.edu', DATE '2025-08-25', TRUE
FROM department WHERE department_code = 'MATH';

-- A hire date for every active professor, and a rank where the Nebula title names none (NULL keeps Nebula's).
CREATE LOCAL TEMPORARY TABLE demo_faculty (email VARCHAR(255) PRIMARY KEY, professor_rank VARCHAR(20), hire_date DATE);
INSERT INTO demo_faculty VALUES
    ('adele.amberly@example.edu',      NULL,        '2015-08-17'),
    ('ansel.ashcombe@example.edu',     'ASSISTANT', '2023-01-09'),
    ('cassius.birchfield@example.edu', 'ASSOCIATE', '2019-08-19'),
    ('delphine.blackmere@example.edu', NULL,        '2008-08-18'),
    ('emeric.brambleton@example.edu',  'ASSISTANT', '2025-08-25'),
    ('fiona.brightwater@example.edu',  'ASSOCIATE', '2017-08-21'),
    ('hollis.carrowmore@example.edu',  NULL,        '2024-08-19'),
    ('jude.cindervale@example.edu',    NULL,        '2005-08-22'),
    ('kaia.clearbrook@example.edu',    NULL,        '2010-08-23'),
    ('leander.coldwell@example.edu',   'ASSISTANT', '2025-01-27'),
    ('perrin.duskwood@example.edu',    NULL,        '2024-09-02'),
    ('rhea.eastbrook@example.edu',     'ASSISTANT', '2026-01-20'),
    ('thea.emberton@example.edu',      'ASSOCIATE', '2023-01-09'),
    ('ulric.fairhaven@example.edu',    NULL,        '2014-08-18'),
    ('vivian.fernsby@example.edu',     'FULL',      '2012-01-09'),
    ('arlo.glimmerton@example.edu',    NULL,        '2016-08-15'),
    ('bianca.goldmere@example.edu',    NULL,        '2009-08-17'),
    ('cyrus.greystone@example.edu',    'ASSISTANT', '2025-01-21'),
    ('elio.harrowgate@example.edu',    'ASSOCIATE', '2025-01-21'),
    ('avery.hazelwood@example.edu',    'ASSOCIATE', '2018-08-20'),
    ('dario.hollowell@example.edu',    'ASSISTANT', '2025-09-15'),
    ('elena.ironwood@example.edu',     'ASSISTANT', '2025-08-25'),
    ('greta.ivorydale@example.edu',    NULL,        '2023-06-05'),
    ('hiro.juniper@example.edu',       NULL,        '2026-01-20'),
    ('jonas.lakeshore@example.edu',    NULL,        '2023-08-21'),
    ('keira.larkspur@example.edu',     NULL,        '2024-08-19'),
    ('luca.lindenhall@example.edu',    'ASSOCIATE', '2024-01-16'),
    ('mira.marchbank@example.edu',     'ASSISTANT', '2025-08-25'),
    ('orla.merriweather@example.edu',  'ASSOCIATE', '2023-08-21'),
    ('pavel.millbrook@example.edu',    NULL,        '2025-08-25'),
    ('quinn.moorfield@example.edu',    NULL,        '2021-08-16'),
    ('soren.oakhurst@example.edu',     'FULL',      '2007-08-20'),
    ('tamsin.oldcastle@example.edu',   NULL,        '2022-08-22'),
    ('vera.quarrington@example.edu',   'ASSISTANT', '2026-01-20'),
    ('wren.ravenhill@example.edu',     'ASSOCIATE', '2025-01-21'),
    ('xiomara.ridgeway@example.edu',   'FULL',      '2013-01-07'),
    ('yusuf.rookwood@example.edu',     'ASSISTANT', '2026-06-01'),
    ('anika.saltmarsh@example.edu',    NULL,        '2011-08-22'),
    ('bram.silverton@example.edu',     NULL,        '2023-01-09'),
    ('devika.stonebridge@example.edu', 'ASSOCIATE', '2024-08-19'),
    ('felix.thornbury@example.edu',    'ASSISTANT', '2026-01-20'),
    ('gemma.underhill@example.edu',    NULL,        '2006-08-21'),
    ('hugo.valemont@example.edu',      'ASSISTANT', '2026-01-20'),
    ('kofi.winterbourne@example.edu',  'ASSISTANT', '2026-08-24');
UPDATE professor p SET
    professor_rank = COALESCE(p.professor_rank, (SELECT d.professor_rank FROM demo_faculty d WHERE d.email = p.email)),
    hire_date = (SELECT d.hire_date FROM demo_faculty d WHERE d.email = p.email)
WHERE p.email IN (SELECT email FROM demo_faculty);
DROP TABLE demo_faculty;

-- Kaia is on the Assessment Committee. Passwords: Committee123! for Kaia and Faculty123! for Dario and Arlo.
INSERT INTO committee_member (professor_id, start_date, end_date, active_status)
SELECT professor_id, DATE '2026-01-01', NULL, TRUE FROM professor WHERE email = 'kaia.clearbrook@example.edu';

INSERT INTO user_account (professor_id, username, email, password_hash, is_active)
SELECT professor_id, 'kclearbrook', email, '{bcrypt}$2a$10$i1Ey/aeGhBiqRUbMA6.xqeu0jdkRqIiJNQUQwlRrjcrdPIR7yFkEi', TRUE
FROM professor WHERE email = 'kaia.clearbrook@example.edu';
INSERT INTO user_account (professor_id, username, email, password_hash, is_active)
SELECT professor_id, 'dhollowell', email, '{bcrypt}$2a$10$RseEb1wE5zGshVN6rxKn3.vIn6X9E8zFPbcQHpQgqusGsctp2rydW', TRUE
FROM professor WHERE email = 'dario.hollowell@example.edu';
INSERT INTO user_account (professor_id, username, email, password_hash, is_active)
SELECT professor_id, 'aglimmerton', email, '{bcrypt}$2a$10$RseEb1wE5zGshVN6rxKn3.vIn6X9E8zFPbcQHpQgqusGsctp2rydW', TRUE
FROM professor WHERE email = 'arlo.glimmerton@example.edu';

INSERT INTO observation_template (created_by, template_name, version, created_date, active_status)
SELECT user_id, 'Standard Classroom Observation', 1, DATE '2024-08-01', TRUE
FROM user_account WHERE email = 'kaia.clearbrook@example.edu';

-- Completed evaluations from earlier semesters, each in a section the professor taught that semester. With the
-- ranks and hire dates above, 13 active professors are due in Fall 2026, 8 are overdue and 24 are upcoming.
CREATE LOCAL TEMPORARY TABLE demo_history (
    email VARCHAR(255) PRIMARY KEY, term VARCHAR(10), academic_year INT, completed_on DATE, observer_email VARCHAR(255));
INSERT INTO demo_history VALUES
    ('quinn.moorfield@example.edu',    'Fall',   2022, '2022-10-18', 'bianca.goldmere@example.edu'),
    ('cassius.birchfield@example.edu', 'Spring', 2023, '2023-03-07', 'bianca.goldmere@example.edu'),
    ('jude.cindervale@example.edu',    'Fall',   2023, '2023-10-17', 'gemma.underhill@example.edu'),
    ('anika.saltmarsh@example.edu',    'Fall',   2023, '2023-11-07', 'gemma.underhill@example.edu'),
    ('delphine.blackmere@example.edu', 'Spring', 2024, '2024-03-05', 'gemma.underhill@example.edu'),
    ('ulric.fairhaven@example.edu',    'Spring', 2024, '2024-04-02', 'gemma.underhill@example.edu'),
    ('soren.oakhurst@example.edu',     'Fall',   2024, '2024-09-24', 'kaia.clearbrook@example.edu'),
    ('adele.amberly@example.edu',      'Fall',   2024, '2024-10-15', 'kaia.clearbrook@example.edu'),
    ('arlo.glimmerton@example.edu',    'Fall',   2024, '2024-10-16', 'kaia.clearbrook@example.edu'),
    ('avery.hazelwood@example.edu',    'Fall',   2024, '2024-10-22', 'kaia.clearbrook@example.edu'),
    ('xiomara.ridgeway@example.edu',   'Fall',   2024, '2024-10-29', 'kaia.clearbrook@example.edu'),
    ('vivian.fernsby@example.edu',     'Fall',   2024, '2024-11-05', 'kaia.clearbrook@example.edu'),
    ('orla.merriweather@example.edu',  'Fall',   2024, '2024-11-12', 'kaia.clearbrook@example.edu'),
    ('tamsin.oldcastle@example.edu',   'Spring', 2025, '2025-02-25', 'kaia.clearbrook@example.edu'),
    ('gemma.underhill@example.edu',    'Spring', 2025, '2025-03-04', 'kaia.clearbrook@example.edu'),
    ('kaia.clearbrook@example.edu',    'Spring', 2025, '2025-03-11', 'gemma.underhill@example.edu'),
    ('thea.emberton@example.edu',      'Spring', 2025, '2025-03-25', 'kaia.clearbrook@example.edu'),
    ('jonas.lakeshore@example.edu',    'Spring', 2025, '2025-04-08', 'kaia.clearbrook@example.edu'),
    ('luca.lindenhall@example.edu',    'Fall',   2025, '2025-10-07', 'arlo.glimmerton@example.edu'),
    ('fiona.brightwater@example.edu',  'Fall',   2025, '2025-10-14', 'arlo.glimmerton@example.edu'),
    ('hollis.carrowmore@example.edu',  'Fall',   2025, '2025-10-21', 'arlo.glimmerton@example.edu'),
    ('keira.larkspur@example.edu',     'Fall',   2025, '2025-10-28', 'arlo.glimmerton@example.edu'),
    ('bianca.goldmere@example.edu',    'Fall',   2025, '2025-11-04', 'arlo.glimmerton@example.edu'),
    ('bram.silverton@example.edu',     'Spring', 2026, '2026-02-17', 'arlo.glimmerton@example.edu'),
    ('cyrus.greystone@example.edu',    'Spring', 2026, '2026-02-24', 'arlo.glimmerton@example.edu'),
    ('ansel.ashcombe@example.edu',     'Spring', 2026, '2026-03-03', 'arlo.glimmerton@example.edu'),
    ('elena.ironwood@example.edu',     'Spring', 2026, '2026-03-10', 'arlo.glimmerton@example.edu'),
    ('devika.stonebridge@example.edu', 'Spring', 2026, '2026-03-31', 'arlo.glimmerton@example.edu'),
    ('mira.marchbank@example.edu',     'Spring', 2026, '2026-04-14', 'arlo.glimmerton@example.edu');

INSERT INTO evaluation_cycle (semester_id, signup_deadline, observation_deadline, feedback_deadline, status)
SELECT s.semester_id, DATEADD('DAY', 21, s.start_date), DATEADD('DAY', -14, s.end_date), s.end_date, 'CLOSED'
FROM semester s
WHERE EXISTS (SELECT 1 FROM demo_history h WHERE h.term = s.term AND h.academic_year = s.academic_year)
ORDER BY s.start_date;

INSERT INTO observee (professor_id, cycle_id, status)
SELECT p.professor_id, c.cycle_id, 'COMPLETED'
FROM demo_history h JOIN professor p ON p.email = h.email
JOIN semester s ON s.term = h.term AND s.academic_year = h.academic_year
JOIN evaluation_cycle c ON c.semester_id = s.semester_id;

INSERT INTO observer (professor_id, cycle_id, active_status)
SELECT DISTINCT p.professor_id, c.cycle_id, TRUE
FROM demo_history h JOIN professor p ON p.email = h.observer_email
JOIN semester s ON s.term = h.term AND s.academic_year = h.academic_year
JOIN evaluation_cycle c ON c.semester_id = s.semester_id;

INSERT INTO evaluation_signup (observee_id, section_id, signup_date, status)
SELECT o.observee_id, MIN(cs.section_id), MIN(DATEADD('DAY', 14, s.start_date)), 'COMPLETED'
FROM demo_history h JOIN professor p ON p.email = h.email
JOIN semester s ON s.term = h.term AND s.academic_year = h.academic_year
JOIN evaluation_cycle c ON c.semester_id = s.semester_id
JOIN observee o ON o.professor_id = p.professor_id AND o.cycle_id = c.cycle_id
JOIN teaching_history t ON t.professor_id = p.professor_id
JOIN course_section cs ON cs.section_id = t.section_id AND cs.semester_id = s.semester_id
GROUP BY o.observee_id;

INSERT INTO observation (signup_id, observer_id, template_id, observation_date, start_time, status,
    observee_signed, observer_signed, observee_signed_at, observer_signed_at, completed_at)
SELECT es.signup_id, ob.observer_id, (SELECT MIN(template_id) FROM observation_template), h.completed_on,
    cs.start_time, 'COMPLETED', TRUE, TRUE, DATEADD('HOUR', 16, CAST(h.completed_on AS TIMESTAMP)),
    DATEADD('HOUR', 15, CAST(h.completed_on AS TIMESTAMP)), DATEADD('HOUR', 16, CAST(h.completed_on AS TIMESTAMP))
FROM evaluation_signup es
JOIN observee o ON o.observee_id = es.observee_id
JOIN course_section cs ON cs.section_id = es.section_id
JOIN professor p ON p.professor_id = o.professor_id
JOIN demo_history h ON h.email = p.email
JOIN professor op ON op.email = h.observer_email
JOIN observer ob ON ob.professor_id = op.professor_id AND ob.cycle_id = o.cycle_id;
DROP TABLE demo_history;

-- Fall 2026: six CS 3345 instructors have signed up, so they form the observer pool. Dario (CS 3345.005,
-- TR 13:00-14:15) has not signed up yet. When he does, Leander, Arlo, Avery and Keira are eligible; Xiomara and
-- Perrin teach at the same time, so they are not. Hollis (three CS 2336 sections) is due but has not signed up.
CREATE LOCAL TEMPORARY TABLE demo_signup (email VARCHAR(255) PRIMARY KEY);
INSERT INTO demo_signup VALUES
    ('leander.coldwell@example.edu'), ('arlo.glimmerton@example.edu'), ('avery.hazelwood@example.edu'),
    ('keira.larkspur@example.edu'), ('xiomara.ridgeway@example.edu'), ('perrin.duskwood@example.edu');

INSERT INTO observee (professor_id, cycle_id, status)
SELECT p.professor_id, c.cycle_id, 'ACTIVE'
FROM demo_signup d JOIN professor p ON p.email = d.email CROSS JOIN demo_cycle c;

INSERT INTO observer (professor_id, cycle_id, active_status)
SELECT p.professor_id, c.cycle_id, TRUE
FROM demo_signup d JOIN professor p ON p.email = d.email CROSS JOIN demo_cycle c;

-- Each signs up with their first CS 3345 section of the semester.
INSERT INTO evaluation_signup (observee_id, section_id, signup_date, status)
SELECT o.observee_id, MIN(cs.section_id), DATE '2026-10-12', 'PENDING'
FROM demo_signup d JOIN professor p ON p.email = d.email
CROSS JOIN demo_cycle c
JOIN observee o ON o.professor_id = p.professor_id AND o.cycle_id = c.cycle_id
JOIN teaching_history t ON t.professor_id = p.professor_id
JOIN course_section cs ON cs.section_id = t.section_id AND cs.semester_id = c.semester_id
JOIN course co ON co.course_id = cs.course_id AND co.course_number = '3345'
GROUP BY o.observee_id;

INSERT INTO observer_list_state (signup_id, window_start, window_end)
SELECT es.signup_id, DATE '2026-11-01', DATE '2026-11-15'
FROM evaluation_signup es JOIN observee o ON o.observee_id = es.observee_id JOIN demo_cycle c ON c.cycle_id = o.cycle_id;
DROP TABLE demo_signup;

-- The committee has already sent Leander's and Avery's lists. Leander asked Keira, who accepted, so his observation
-- is scheduled; Avery has not asked anyone yet. The other four lists fill in when someone signs up or the committee
-- clicks Generate Matches.
CREATE LOCAL TEMPORARY TABLE demo_match (seq INT PRIMARY KEY, observee_email VARCHAR(255), observer_email VARCHAR(255));
INSERT INTO demo_match VALUES
    (1, 'leander.coldwell@example.edu', 'avery.hazelwood@example.edu'),
    (2, 'leander.coldwell@example.edu', 'keira.larkspur@example.edu'),
    (3, 'leander.coldwell@example.edu', 'xiomara.ridgeway@example.edu'),
    (4, 'leander.coldwell@example.edu', 'perrin.duskwood@example.edu'),
    (5, 'avery.hazelwood@example.edu',  'leander.coldwell@example.edu'),
    (6, 'avery.hazelwood@example.edu',  'keira.larkspur@example.edu'),
    (7, 'avery.hazelwood@example.edu',  'xiomara.ridgeway@example.edu'),
    (8, 'avery.hazelwood@example.edu',  'perrin.duskwood@example.edu');
INSERT INTO observer_match (signup_id, observer_id, generated_date, match_status)
SELECT es.signup_id, ob.observer_id, DATE '2026-10-12', 'PROPOSED'
FROM demo_match m CROSS JOIN demo_cycle c
JOIN professor pe ON pe.email = m.observee_email
JOIN observee oe ON oe.professor_id = pe.professor_id AND oe.cycle_id = c.cycle_id
JOIN evaluation_signup es ON es.observee_id = oe.observee_id
JOIN professor po ON po.email = m.observer_email
JOIN observer ob ON ob.professor_id = po.professor_id AND ob.cycle_id = c.cycle_id
ORDER BY m.seq;
DROP TABLE demo_match;

CREATE LOCAL TEMPORARY TABLE demo_story AS
SELECT
    (SELECT es.signup_id FROM evaluation_signup es JOIN observee o ON o.observee_id = es.observee_id
     JOIN professor p ON p.professor_id = o.professor_id JOIN demo_cycle c ON c.cycle_id = o.cycle_id
     WHERE p.email = 'leander.coldwell@example.edu') AS leander_signup,
    (SELECT es.signup_id FROM evaluation_signup es JOIN observee o ON o.observee_id = es.observee_id
     JOIN professor p ON p.professor_id = o.professor_id JOIN demo_cycle c ON c.cycle_id = o.cycle_id
     WHERE p.email = 'avery.hazelwood@example.edu') AS avery_signup,
    (SELECT ob.observer_id FROM observer ob JOIN professor p ON p.professor_id = ob.professor_id
     JOIN demo_cycle c ON c.cycle_id = ob.cycle_id WHERE p.email = 'keira.larkspur@example.edu') AS keira_observer;

UPDATE observer_list_state SET released_at = TIMESTAMP '2026-10-14 09:00:00'
WHERE signup_id = (SELECT leander_signup FROM demo_story);
UPDATE observer_list_state SET released_at = TIMESTAMP '2026-10-19 09:00:00'
WHERE signup_id = (SELECT avery_signup FROM demo_story);

INSERT INTO observer_request (signup_id, observer_id, request_date, expiration_date, response_date, status)
SELECT leander_signup, keira_observer, TIMESTAMP '2026-10-15 09:00:00', TIMESTAMP '2026-10-22 09:00:00',
    TIMESTAMP '2026-10-16 14:00:00', 'CONFIRMED'
FROM demo_story;
INSERT INTO observation (signup_id, observer_id, template_id, observation_date, start_time, status)
SELECT leander_signup, keira_observer, (SELECT MIN(template_id) FROM observation_template), DATE '2026-11-04',
    TIME '08:30:00', 'SCHEDULED'
FROM demo_story;
UPDATE evaluation_signup SET status = 'MATCHED' WHERE signup_id = (SELECT leander_signup FROM demo_story);

-- The in-app notifications those steps would have sent.
INSERT INTO observer_notification (professor_id, signup_id, message, created_at)
SELECT p.professor_id, d.leander_signup, n.message, n.created_at
FROM demo_story d CROSS JOIN (VALUES
    ('leander.coldwell@example.edu', 'Your generated observer list is ready. Choose an observer to request.',
        TIMESTAMP '2026-10-14 09:00:00'),
    ('keira.larkspur@example.edu', 'Leander Coldwell requested an observation of CS 3345.001 (MW 08:30-09:45).',
        TIMESTAMP '2026-10-15 09:00:00'),
    ('leander.coldwell@example.edu', 'Keira Larkspur accepted your observation request for CS 3345.001.',
        TIMESTAMP '2026-10-16 14:00:00')) AS n(email, message, created_at)
JOIN professor p ON p.email = n.email;
INSERT INTO observer_notification (professor_id, signup_id, message, created_at)
SELECT p.professor_id, d.avery_signup, 'Your generated observer list is ready. Choose an observer to request.',
    TIMESTAMP '2026-10-19 09:00:00'
FROM demo_story d JOIN professor p ON p.email = 'avery.hazelwood@example.edu';

DROP TABLE demo_story;
DROP TABLE demo_cycle;
