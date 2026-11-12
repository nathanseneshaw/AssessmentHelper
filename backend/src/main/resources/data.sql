-- Seed data for the Assessment Helper. Spring Boot runs this file after schema.sql at startup, and the backend tests
-- run against it.
--
-- It loads schools, departments, professors, semesters, courses, class sections and who taught each section, copied
-- from the UTD Nebula API with made-up professor names and emails. It also loads made-up demo data that Nebula does
-- not have: ranks, hire dates, evaluation history, user accounts and the current evaluation cycle.
--
-- Ids are generated in insert order, and the tests refer to some of them. Add new rows after the existing rows of a
-- table rather than between them, so those ids stay the same.

-- =====================================================================================================================
-- Data from the UTD Nebula API
-- =====================================================================================================================

-- The UTD schools the departments below belong to.
INSERT INTO school (school_name) VALUES
    ('Erik Jonsson School of Engineering and Computer Science'),
    ('School of Natural Sciences and Mathematics'),
    ('Naveen Jindal School of Management'),
    ('School of Arts, Humanities, and Technology');

-- The code is the department's course prefix, except ATEC, whose courses use the ATCM prefix.
INSERT INTO department (school_id, department_name, department_code) VALUES
    (1, 'Computer Science', 'CS'),
    (2, 'Mathematics', 'MATH'),
    (1, 'Electrical and Computer Engineering', 'EE'),
    (1, 'Mechanical Engineering', 'MECH'),
    (1, 'Bioengineering', 'BMEN'),
    (2, 'Physics', 'PHYS'),
    (2, 'Chemistry', 'CHEM'),
    (2, 'Biological Sciences', 'BIOL'),
    (3, 'Accounting', 'ACCT'),
    (3, 'Finance', 'FIN'),
    (4, 'Arts, Technology, and Emerging Communication', 'ATEC'),
    (4, 'Historical Studies', 'HIST');

-- How often each rank is evaluated. Professors refer to these ranks, so this comes before them.
-- professor_rank is the primary key of this table.
INSERT INTO evaluation_policy (professor_rank, evaluation_interval_years, active_status, made_date, effective_date, version) VALUES
    ('ASSISTANT', 1, TRUE, '2026-01-01', '2026-01-01', 0),
    ('ASSOCIATE', 2, TRUE, '2026-01-01', '2026-01-01', 0),
    ('FULL',      2, TRUE, '2026-01-01', '2026-01-01', 0);

-- Instructors of the courses below from Fall 2020 to Spring 2027, copied from the UTD Nebula API. Rank comes
-- from the Nebula title when it names a professor rank, otherwise it is NULL. Nebula has no hire dates.
-- Instructors who have not taught since Fall 2025 are inactive.
-- Names and emails are made up to keep the instructors anonymous; everything else is from Nebula.
INSERT INTO professor (department_id, professor_rank, first_name, last_name, email, hire_date, active_status) VALUES
    -- Computer Science
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
    (1, NULL,        'Kofi',     'Winterbourne', 'kofi.winterbourne@example.edu',  NULL, TRUE),
    -- Computer Science instructors who only taught the Fall 2020 to Summer 2022 sections
    (1, 'FULL',      'Tariq',    'Alderwick',    'tariq.alderwick@example.edu',    NULL, FALSE),
    (1, 'FULL',      'Ivo',      'Applethorne',  'ivo.applethorne@example.edu',    NULL, FALSE),
    (1, 'FULL',      'Daphne',   'Ashenford',    'daphne.ashenford@example.edu',   NULL, FALSE),
    (1, NULL,        'Lena',     'Barrowcliff',  'lena.barrowcliff@example.edu',   NULL, FALSE),
    (1, NULL,        'Ezra',     'Beechcombe',   'ezra.beechcombe@example.edu',    NULL, FALSE),
    -- Mathematics
    (2, 'ASSISTANT', 'Joaquin',  'Bellmoor',     'joaquin.bellmoor@example.edu',   NULL, TRUE),
    (2, 'ASSOCIATE', 'Abel',     'Blythewood',   'abel.blythewood@example.edu',    NULL, TRUE),
    (2, 'ASSOCIATE', 'Ravi',     'Bramblecote',  'ravi.bramblecote@example.edu',   NULL, TRUE),
    (2, 'FULL',      'Matteo',   'Briarwood',    'matteo.briarwood@example.edu',   NULL, FALSE),
    (2, 'FULL',      'Bryony',   'Brindlecombe', 'bryony.brindlecombe@example.edu', NULL, FALSE),
    (2, 'ASSOCIATE', 'Vesna',    'Burnhollow',   'vesna.burnhollow@example.edu',   NULL, TRUE),
    (2, 'FULL',      'Benedikt', 'Calderwood',   'benedikt.calderwood@example.edu', NULL, TRUE),
    (2, NULL,        'Kasimir',  'Cedarholt',    'kasimir.cedarholt@example.edu',  NULL, FALSE),
    -- Electrical and Computer Engineering
    (3, 'FULL',      'Linnea',   'Cloverdale',   'linnea.cloverdale@example.edu',  NULL, FALSE),
    (3, 'FULL',      'Aurelio',  'Copperhall',   'aurelio.copperhall@example.edu', NULL, TRUE),
    (3, 'FULL',      'Amara',    'Cragmoor',     'amara.cragmoor@example.edu',     NULL, TRUE),
    (3, 'FULL',      'Cecily',   'Crestwater',   'cecily.crestwater@example.edu',  NULL, TRUE),
    (3, 'FULL',      'Ingrid',   'Daisybank',    'ingrid.daisybank@example.edu',   NULL, TRUE),
    (3, 'ASSOCIATE', 'Valentin', 'Dappleton',    'valentin.dappleton@example.edu', NULL, TRUE),
    (3, 'FULL',      'Kenji',    'Dovecote',     'kenji.dovecote@example.edu',     NULL, FALSE),
    (3, NULL,        'Tobias',   'Driftmere',    'tobias.driftmere@example.edu',   NULL, FALSE),
    -- Mechanical Engineering
    (4, 'ASSOCIATE', 'Bettina',  'Elmsworth',    'bettina.elmsworth@example.edu',  NULL, TRUE),
    (4, NULL,        'Giulia',   'Embervale',    'giulia.embervale@example.edu',   NULL, TRUE),
    (4, 'ASSOCIATE', 'Mabel',    'Evenwood',     'mabel.evenwood@example.edu',     NULL, TRUE),
    (4, NULL,        'Rufus',    'Fallowmere',   'rufus.fallowmere@example.edu',   NULL, TRUE),
    (4, 'FULL',      'Otis',     'Fernhollow',   'otis.fernhollow@example.edu',    NULL, FALSE),
    (4, NULL,        'Gaspard',  'Fieldgate',    'gaspard.fieldgate@example.edu',  NULL, TRUE),
    (4, NULL,        'Mireille', 'Flintwick',    'mireille.flintwick@example.edu', NULL, FALSE),
    -- Bioengineering
    (5, 'FULL',      'Caspian',  'Galeborne',    'caspian.galeborne@example.edu',  NULL, FALSE),
    (5, 'ASSISTANT', 'Lorcan',   'Gildenmoor',   'lorcan.gildenmoor@example.edu',  NULL, TRUE),
    (5, 'FULL',      'Henrik',   'Glassbrook',   'henrik.glassbrook@example.edu',  NULL, FALSE),
    (5, 'ASSISTANT', 'Hana',     'Gorsefield',   'hana.gorsefield@example.edu',    NULL, TRUE),
    (5, NULL,        'Adaeze',   'Hartmoor',     'adaeze.hartmoor@example.edu',    NULL, FALSE),
    (5, NULL,        'Katya',    'Hawkridge',    'katya.hawkridge@example.edu',    NULL, TRUE),
    (5, NULL,        'Corin',    'Heronsgate',   'corin.heronsgate@example.edu',   NULL, TRUE),
    -- Physics
    (6, 'FULL',      'Zoltan',   'Hollyfield',   'zoltan.hollyfield@example.edu',  NULL, TRUE),
    (6, 'FULL',      'Lior',     'Holmewood',    'lior.holmewood@example.edu',     NULL, TRUE),
    (6, 'ASSOCIATE', 'Dmitri',   'Ivybridge',    'dmitri.ivybridge@example.edu',   NULL, FALSE),
    (6, NULL,        'Noor',     'Jadecombe',    'noor.jadecombe@example.edu',     NULL, TRUE),
    (6, NULL,        'Emrys',    'Kettleby',     'emrys.kettleby@example.edu',     NULL, FALSE),
    (6, NULL,        'Anders',   'Kingsmere',    'anders.kingsmere@example.edu',   NULL, FALSE),
    (6, NULL,        'Ilse',     'Lanternfield', 'ilse.lanternfield@example.edu',  NULL, FALSE),
    (6, NULL,        'Leopold',  'Lavenham',     'leopold.lavenham@example.edu',   NULL, TRUE),
    -- Chemistry
    (7, 'ASSOCIATE', 'Sabine',   'Leafwood',     'sabine.leafwood@example.edu',    NULL, TRUE),
    (7, 'ASSOCIATE', 'Aiko',     'Linnetford',   'aiko.linnetford@example.edu',    NULL, TRUE),
    (7, 'ASSISTANT', 'Oskar',    'Loamsby',      'oskar.loamsby@example.edu',      NULL, TRUE),
    (7, 'ASSOCIATE', 'Eamon',    'Maplethorpe',  'eamon.maplethorpe@example.edu',  NULL, TRUE),
    (7, 'FULL',      'Alma',     'Mistlecombe',  'alma.mistlecombe@example.edu',   NULL, TRUE),
    (7, 'ASSISTANT', 'Saoirse',  'Mossgrove',    'saoirse.mossgrove@example.edu',  NULL, FALSE),
    (7, NULL,        'Quentin',  'Nettlefold',   'quentin.nettlefold@example.edu', NULL, FALSE),
    (7, NULL,        'Gwen',     'Oakenshaw',    'gwen.oakenshaw@example.edu',     NULL, FALSE),
    (7, NULL,        'Freya',    'Orchardleigh', 'freya.orchardleigh@example.edu', NULL, TRUE),
    -- Biological Sciences
    (8, 'ASSISTANT', 'Desmond',  'Otterburn',    'desmond.otterburn@example.edu',  NULL, FALSE),
    (8, 'ASSISTANT', 'Yvette',   'Pebbleton',    'yvette.pebbleton@example.edu',   NULL, FALSE),
    (8, 'ASSISTANT', 'Malik',    'Pinegrove',    'malik.pinegrove@example.edu',    NULL, TRUE),
    (8, 'ASSISTANT', 'Ulla',     'Quillfeather', 'ulla.quillfeather@example.edu',  NULL, TRUE),
    (8, 'FULL',      'Nadia',    'Quinceworth',  'nadia.quinceworth@example.edu',  NULL, TRUE),
    (8, 'ASSOCIATE', 'Jalen',    'Reedmoor',     'jalen.reedmoor@example.edu',     NULL, TRUE),
    (8, 'ASSISTANT', 'Xavier',   'Ripplewood',   'xavier.ripplewood@example.edu',  NULL, FALSE),
    (8, 'ASSISTANT', 'Niamh',    'Rushcombe',    'niamh.rushcombe@example.edu',    NULL, TRUE),
    (8, NULL,        'Calla',    'Sagebrook',    'calla.sagebrook@example.edu',    NULL, TRUE),
    -- Accounting
    (9, NULL,        'Selim',    'Sandmere',     'selim.sandmere@example.edu',     NULL, FALSE),
    (9, NULL,        'Rosalind', 'Shadowfen',    'rosalind.shadowfen@example.edu', NULL, TRUE),
    (9, NULL,        'Priya',    'Skylark',      'priya.skylark@example.edu',      NULL, TRUE),
    (9, NULL,        'Faye',     'Sloeberry',    'faye.sloeberry@example.edu',     NULL, FALSE),
    (9, NULL,        'Paloma',   'Sparrowhawk',  'paloma.sparrowhawk@example.edu', NULL, TRUE),
    (9, NULL,        'Idris',    'Swallowdale',  'idris.swallowdale@example.edu',  NULL, TRUE),
    (9, NULL,        'Florian',  'Tamberlyn',    'florian.tamberlyn@example.edu',  NULL, TRUE),
    (9, NULL,        'Clio',     'Thornfield',   'clio.thornfield@example.edu',    NULL, TRUE),
    -- Finance
    (10, 'ASSISTANT', 'Frida',    'Tidewell',     'frida.tidewell@example.edu',     NULL, FALSE),
    (10, NULL,        'Elif',     'Timberlake',   'elif.timberlake@example.edu',    NULL, TRUE),
    (10, NULL,        'Halvard',  'Umberleigh',   'halvard.umberleigh@example.edu', NULL, FALSE),
    (10, NULL,        'Ramona',   'Vervain',      'ramona.vervain@example.edu',     NULL, TRUE),
    (10, NULL,        'Chidi',    'Wainscott',    'chidi.wainscott@example.edu',    NULL, FALSE),
    (10, NULL,        'August',   'Wheatcombe',   'august.wheatcombe@example.edu',  NULL, FALSE),
    (10, NULL,        'Wilhelmina', 'Whitlow',      'wilhelmina.whitlow@example.edu', NULL, TRUE),
    (10, NULL,        'Hector',   'Yarrowby',     'hector.yarrowby@example.edu',    NULL, TRUE),
    (10, NULL,        'Meera',    'Yewbank',      'meera.yewbank@example.edu',      NULL, TRUE),
    -- Arts, Technology, and Emerging Communication
    (11, 'ASSISTANT', 'Delia',    'Aspenridge',   'delia.aspenridge@example.edu',   NULL, TRUE),
    (11, 'ASSOCIATE', 'Cormac',   'Chalkridge',   'cormac.chalkridge@example.edu',  NULL, TRUE),
    (11, NULL,        'Iona',     'Copperdale',   'iona.copperdale@example.edu',    NULL, FALSE),
    (11, 'ASSISTANT', 'Elodie',   'Elmhollow',    'elodie.elmhollow@example.edu',   NULL, TRUE),
    (11, NULL,        'Imogen',   'Fenwright',    'imogen.fenwright@example.edu',   NULL, TRUE),
    (11, NULL,        'Cosima',   'Foxglove',     'cosima.foxglove@example.edu',    NULL, FALSE),
    (11, NULL,        'Nils',     'Hazelgrove',   'nils.hazelgrove@example.edu',    NULL, FALSE),
    -- Historical Studies
    (12, 'ASSISTANT', 'Marguerite', 'Inglemoor',    'marguerite.inglemoor@example.edu', NULL, TRUE),
    (12, 'ASSOCIATE', 'Edda',     'Mallowbrook',  'edda.mallowbrook@example.edu',   NULL, TRUE),
    (12, 'FULL',      'Junia',    'Netherby',     'junia.netherby@example.edu',     NULL, TRUE),
    (12, 'FULL',      'Tilde',    'Pennywhistle', 'tilde.pennywhistle@example.edu', NULL, FALSE),
    (12, 'ASSOCIATE', 'Petra',    'Rowanberry',   'petra.rowanberry@example.edu',   NULL, TRUE),
    (12, NULL,        'Greer',    'Thistledown',  'greer.thistledown@example.edu',  NULL, TRUE),
    (12, NULL,        'Basil',    'Willowmere',   'basil.willowmere@example.edu',   NULL, FALSE);

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

-- Older semesters, Fall 2020 to Summer 2022, added after the ones above so their ids stay the same.
INSERT INTO semester (term, academic_year, start_date, end_date) VALUES
    ('Fall',   2020, '2020-08-17', '2020-12-08'),
    ('Spring', 2021, '2021-01-19', '2021-05-15'),
    ('Summer', 2021, '2021-05-24', '2021-08-09'),
    ('Fall',   2021, '2021-08-23', '2021-12-15'),
    ('Spring', 2022, '2022-01-18', '2022-05-13'),
    ('Summer', 2022, '2022-05-23', '2022-08-10');

-- Courses copied from the UTD Nebula API (2026 catalog)
INSERT INTO course (department_id, course_code, course_number, course_name, course_level) VALUES
    -- Computer Science
    (1, 'CS', '1337', 'Computer Science I', 'UNDERGRADUATE'),
    (1, 'CS', '2336', 'Computer Science II', 'UNDERGRADUATE'),
    (1, 'CS', '3345', 'Data Structures and Foundations of Algorithmic Analysis', 'UNDERGRADUATE'),
    (1, 'CS', '4485', 'Computer Science Project', 'UNDERGRADUATE'),
    (1, 'CS', '6363', 'Design and Analysis of Computer Algorithms', 'GRADUATE'),
    -- Mathematics
    (2, 'MATH', '3351', 'Advanced Calculus', 'UNDERGRADUATE'),
    (2, 'MATH', '3311', 'Abstract Algebra I', 'UNDERGRADUATE'),
    -- Electrical and Computer Engineering
    (3, 'EE', '3311', 'Electronic Circuits', 'UNDERGRADUATE'),
    -- Mechanical Engineering
    (4, 'MECH', '3310', 'Thermodynamics', 'UNDERGRADUATE'),
    (4, 'MECH', '3350', 'Kinematics and Dynamics of Mechanical Systems', 'UNDERGRADUATE'),
    -- Bioengineering
    (5, 'BMEN', '3315', 'Thermodynamics and Physical Chemistry in Biomedical Engineering', 'UNDERGRADUATE'),
    (5, 'BMEN', '3399', 'Introductory Biomechanics', 'UNDERGRADUATE'),
    -- Physics
    (6, 'PHYS', '3411', 'Theoretical Physics', 'UNDERGRADUATE'),
    (6, 'PHYS', '3427', 'Electronics with Laboratory', 'UNDERGRADUATE'),
    (6, 'PHYS', '4373', 'Physical Measurements Laboratory', 'UNDERGRADUATE'),
    -- Chemistry
    (7, 'CHEM', '3321', 'Physical Chemistry I', 'UNDERGRADUATE'),
    (7, 'CHEM', '3322', 'Physical Chemistry II', 'UNDERGRADUATE'),
    (7, 'CHEM', '3472', 'Instrumental Analysis', 'UNDERGRADUATE'),
    -- Biological Sciences
    (8, 'BIOL', '3303', 'Introduction to Microbiology', 'UNDERGRADUATE'),
    (8, 'BIOL', '3320', 'Applied Genetics', 'UNDERGRADUATE'),
    -- Accounting
    (9, 'ACCT', '3350', 'Fundamentals of Taxation I', 'UNDERGRADUATE'),
    (9, 'ACCT', '4334', 'Auditing', 'UNDERGRADUATE'),
    -- Finance
    (10, 'FIN', '3350', 'Financial Markets and Institutions', 'UNDERGRADUATE'),
    (10, 'FIN', '3370', 'Principles of Risk Management and Insurance', 'UNDERGRADUATE'),
    -- Arts, Technology, and Emerging Communication
    (11, 'ATCM', '4384', 'Ethics in New Media  Technology  and Communication', 'UNDERGRADUATE'),
    (11, 'ATCM', '3388', 'Social Influence and Persuasive Design', 'UNDERGRADUATE'),
    -- Historical Studies
    (12, 'HIST', '2381', 'African-American History', 'UNDERGRADUATE'),
    (12, 'HIST', '3301', 'Historical Inquiry', 'UNDERGRADUATE');

-- Class sections of the CS courses above from Fall 2022 to Spring 2027, copied from the UTD Nebula API.
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

-- More class sections from the UTD Nebula API, added after the ones above so their ids stay the same:
-- the CS courses from Fall 2020 to Summer 2022, and the other courses from Fall 2020 to Spring 2027.
-- Exam sections (numbers starting with 7) are left out.
INSERT INTO course_section (course_id, semester_id, section_number, meeting_days, start_time, end_time, location) VALUES
    -- Fall 2020 (semester 15)
    (1, 15, '001', 'MW', '14:30:00', '15:45:00', 'Online'),
    (1, 15, '002', 'TR', '13:00:00', '14:15:00', 'Online'),
    (1, 15, '501', 'MW', '17:30:00', '18:45:00', 'Online'),
    (1, 15, '504', 'MW', '19:00:00', '20:15:00', 'Online'),
    (2, 15, '001', 'MW', '13:00:00', '14:15:00', 'Online'),
    (2, 15, '002', NULL, NULL, NULL, 'Online'),
    (2, 15, '003', 'TR', '10:00:00', '11:15:00', 'Online'),
    (2, 15, '501', 'TR', '20:30:00', '21:45:00', 'Online'),
    (2, 15, '502', 'TR', '17:30:00', '18:45:00', 'Online'),
    (2, 15, '503', 'TR', '19:00:00', '20:15:00', 'Online'),
    (2, 15, '504', 'TR', '19:00:00', '20:15:00', 'Online'),
    (2, 15, '505', 'TR', '17:30:00', '18:45:00', 'Online'),
    (3, 15, '002', 'MW', '16:00:00', '17:15:00', 'Online'),
    (3, 15, '003', NULL, NULL, NULL, 'Online'),
    (3, 15, '004', 'MW', '08:30:00', '09:45:00', 'Online'),
    (3, 15, '005', 'TR', '08:30:00', '09:45:00', 'Online'),
    (3, 15, '006', 'MW', '13:00:00', '14:15:00', 'Online'),
    (3, 15, '501', 'MW', '17:30:00', '18:45:00', 'Online'),
    (3, 15, '502', 'TR', '19:00:00', '20:15:00', 'Online'),
    (3, 15, 'HON', 'TR', '17:30:00', '18:45:00', 'Online'),
    (4, 15, '001', 'F', '10:00:00', '12:45:00', 'Online'),
    (5, 15, '001', 'MW', '10:00:00', '11:15:00', 'Online'),
    (5, 15, '002', 'MW', '20:30:00', '21:45:00', 'ECSS 2.410'),
    (5, 15, '003', NULL, NULL, NULL, 'Online'),
    (5, 15, '005', 'TR', '14:30:00', '15:45:00', 'Online'),
    (6, 15, '001', 'TR', '08:30:00', '09:45:00', 'HH 2.402'),
    (6, 15, '002', 'TR', '11:30:00', '12:45:00', 'SLC 1.102'),
    (7, 15, '001', 'TR', '10:00:00', '11:15:00', 'Online'),
    (7, 15, '002', 'TR', '11:30:00', '12:45:00', 'Online'),
    (8, 15, '001', 'MW', '10:00:00', '11:15:00', 'Online'),
    (8, 15, '501', 'MW', '17:30:00', '18:45:00', 'Online'),
    (9, 15, '001', 'TR', '08:30:00', '09:45:00', 'Online'),
    (9, 15, '002', 'MW', '14:30:00', '15:45:00', 'Online'),
    (10, 15, '001', NULL, NULL, NULL, 'Online'),
    (10, 15, '501', NULL, NULL, NULL, 'Online'),
    (11, 15, '001', 'MWF', '10:00:00', '10:50:00', 'ECSS 2.412'),
    (11, 15, '501', 'TR', '17:30:00', '18:45:00', 'Online'),
    (12, 15, '001', 'TR', '10:00:00', '11:15:00', 'Online'),
    (13, 15, '001', NULL, NULL, NULL, 'Online'),
    (15, 15, '101', NULL, NULL, NULL, NULL),
    (15, 15, '201', 'M', '14:30:00', '17:15:00', 'SCI 1.188'),
    (15, 15, '202', 'F', '14:30:00', '17:15:00', 'SCI 1.188'),
    (15, 15, '203', 'MF', '14:30:00', '17:15:00', NULL),
    (16, 15, '001', 'MWF', '09:00:00', '09:50:00', 'Online'),
    (17, 15, '001', 'MWF', '11:00:00', '11:50:00', 'Online'),
    (18, 15, '001', 'MW', '11:30:00', '12:45:00', 'Online'),
    (18, 15, '002', 'TR', '11:30:00', '12:45:00', 'Online'),
    (18, 15, '003', 'MW', '16:00:00', '17:15:00', 'Online'),
    (18, 15, '004', 'TR', '08:30:00', '09:45:00', 'Online'),
    (19, 15, '001', 'TR', '08:30:00', '09:45:00', 'Online'),
    (20, 15, '001', NULL, NULL, NULL, 'Online'),
    (20, 15, '002', NULL, NULL, NULL, 'Online'),
    (21, 15, '001', 'TR', '10:00:00', '11:15:00', 'JSOM 1.606'),
    (21, 15, '002', 'TR', '11:30:00', '12:45:00', 'JSOM 1.606'),
    (21, 15, '501', 'M', '19:00:00', '21:45:00', 'ECSS 2.415'),
    (22, 15, '001', 'MW', '13:00:00', '14:15:00', 'Online'),
    (22, 15, '501', 'W', '19:00:00', '21:45:00', 'Online'),
    (22, 15, 'PP1', 'MW', '08:30:00', '09:45:00', 'Online'),
    (22, 15, 'PP2', 'MW', '10:00:00', '11:15:00', 'Online'),
    (23, 15, '001', 'W', '13:00:00', '15:45:00', 'JSOM 1.212'),
    (23, 15, '002', 'T', '10:00:00', '12:45:00', 'JSOM 1.212'),
    (24, 15, '001', 'MW', '10:00:00', '11:15:00', 'Online'),
    (24, 15, '002', 'TR', '10:00:00', '11:15:00', 'Online'),
    (25, 15, '001', 'MW', '10:00:00', '11:15:00', 'Online'),
    (25, 15, '002', 'MW', '11:30:00', '12:45:00', 'Online'),
    (25, 15, '003', 'MW', '13:00:00', '14:15:00', 'Online'),
    (26, 15, '502', NULL, NULL, NULL, 'Online'),
    (28, 15, '001', 'MW', '11:30:00', '12:45:00', 'Online'),
    -- Spring 2021 (semester 16)
    (1, 16, '002', 'MW', '10:00:00', '11:15:00', 'Online'),
    (1, 16, '003', 'MW', '13:00:00', '14:15:00', 'Online'),
    (1, 16, '004', 'TR', '14:30:00', '15:45:00', 'Online'),
    (1, 16, '006', 'MW', '08:30:00', '09:45:00', 'Online'),
    (1, 16, '007', 'TR', '13:00:00', '14:15:00', 'Online'),
    (1, 16, '008', 'TR', '10:00:00', '11:15:00', 'Online'),
    (1, 16, '009', 'TR', '11:30:00', '12:45:00', 'Online'),
    (1, 16, '010', 'MW', '11:30:00', '12:45:00', 'Online'),
    (1, 16, '011', 'TR', '13:00:00', '14:15:00', 'Online'),
    (1, 16, '012', 'MWF', '12:00:00', '12:50:00', 'Online'),
    (1, 16, '014', 'MWF', '15:00:00', '15:50:00', 'Online'),
    (1, 16, '015', 'TR', '16:00:00', '17:15:00', 'Online'),
    (2, 16, '001', 'MW', '10:00:00', '11:15:00', 'Online'),
    (2, 16, '0W1', NULL, NULL, NULL, 'Online'),
    (2, 16, '0W2', NULL, NULL, NULL, 'Online'),
    (2, 16, '501', 'MW', '08:30:00', '09:45:00', 'Online'),
    (3, 16, '001', 'MW', '10:00:00', '11:15:00', 'Online'),
    (3, 16, '002', 'MW', '14:30:00', '15:45:00', 'Online'),
    (3, 16, '003', 'MW', '11:30:00', '12:45:00', 'Online'),
    (3, 16, '004', 'TR', '10:00:00', '11:15:00', 'Online'),
    (3, 16, '005', 'TR', '08:30:00', '09:45:00', 'Online'),
    (3, 16, '0W1', NULL, NULL, NULL, 'Online'),
    (3, 16, '0W2', NULL, NULL, NULL, 'Online'),
    (3, 16, '0W3', NULL, NULL, NULL, 'Online'),
    (3, 16, '501', 'TR', '19:00:00', '20:15:00', 'Online'),
    (3, 16, '502', 'TR', '17:30:00', '18:45:00', 'Online'),
    (4, 16, '001', 'F', '16:00:00', '18:45:00', 'Online'),
    (5, 16, '001', 'TR', '16:00:00', '17:15:00', 'Online'),
    (5, 16, '0W1', 'TR', '10:00:00', '11:15:00', 'Online'),
    (6, 16, '001', 'TR', '11:30:00', '12:45:00', 'SLC 1.102'),
    (6, 16, '002', 'TR', '10:00:00', '11:15:00', 'SCI 1.220'),
    (7, 16, '001', 'TR', '10:00:00', '11:15:00', 'Online'),
    (7, 16, '002', 'TR', '11:30:00', '12:45:00', 'Online'),
    (8, 16, '001', 'TR', '10:00:00', '11:15:00', 'Online'),
    (8, 16, '002', 'MW', '13:00:00', '14:15:00', 'Online'),
    (8, 16, '003', 'MW', '16:00:00', '17:15:00', 'Online'),
    (9, 16, '0W1', NULL, NULL, NULL, 'Online'),
    (9, 16, '0W2', NULL, NULL, NULL, 'Online'),
    (9, 16, '0W3', NULL, NULL, NULL, 'Online'),
    (9, 16, '0W4', NULL, NULL, NULL, 'Online'),
    (9, 16, '201', 'MW', '08:30:00', '09:45:00', 'Online'),
    (10, 16, '0W1', NULL, NULL, NULL, 'Online'),
    (10, 16, '5W1', NULL, NULL, NULL, 'Online'),
    (11, 16, '501', 'TR', '17:30:00', '18:45:00', 'Online'),
    (12, 16, '001', 'TR', '10:00:00', '11:15:00', 'Online'),
    (12, 16, '0W1', NULL, NULL, NULL, 'Online'),
    (13, 16, '001', 'TR', '13:00:00', '14:45:00', 'Online'),
    (14, 16, '501', 'TR', '17:30:00', '18:45:00', 'Online'),
    (14, 16, '801', 'W', '17:30:00', '20:15:00', 'Online'),
    (14, 16, '802', 'F', '17:30:00', '20:15:00', 'Online'),
    (14, 16, '803', 'M', '17:30:00', '20:15:00', 'Online'),
    (15, 16, '1H1', NULL, NULL, NULL, NULL),
    (15, 16, '201', 'M', '14:30:00', '17:15:00', 'SCI 3.250'),
    (15, 16, '202', 'M', '14:30:00', '17:15:00', NULL),
    (16, 16, '001', 'MWF', '13:00:00', '13:50:00', 'Online'),
    (17, 16, '001', 'MWF', '09:00:00', '09:50:00', 'Online'),
    (18, 16, '001', 'MW', '08:30:00', '09:45:00', 'Online'),
    (18, 16, '002', 'MW', '11:30:00', '12:45:00', 'Online'),
    (18, 16, '003', 'TR', '16:00:00', '17:15:00', 'Online'),
    (20, 16, '0W1', NULL, NULL, NULL, 'Online'),
    (20, 16, '0W2', NULL, NULL, NULL, 'Online'),
    (21, 16, '001', 'TR', '10:00:00', '11:15:00', 'JSOM 1.118'),
    (21, 16, '501', 'M', '19:00:00', '21:45:00', 'JSOM 1.217'),
    (21, 16, 'PPA', 'MW', '14:30:00', '15:45:00', 'SU 2.602'),
    (22, 16, '001', NULL, NULL, NULL, NULL),
    (22, 16, '201', 'MW', '08:30:00', '09:45:00', 'JSOM 1.217'),
    (22, 16, '202', 'MW', '08:30:00', '09:45:00', NULL),
    (22, 16, '501', 'W', '19:00:00', '21:45:00', 'Online'),
    (23, 16, '001', 'M', '13:00:00', '15:45:00', 'Online'),
    (23, 16, '002', 'M', '16:00:00', '18:45:00', 'Online'),
    (24, 16, '001', 'MW', '14:30:00', '15:45:00', 'Online'),
    (25, 16, '001', 'MW', '11:30:00', '12:45:00', 'Online'),
    (25, 16, '002', 'MW', '13:00:00', '14:15:00', 'Online'),
    (26, 16, '0W1', NULL, NULL, NULL, 'Online'),
    (27, 16, '001', 'MW', '12:00:00', '12:50:00', 'Online'),
    (27, 16, '201', 'R', '10:00:00', '10:50:00', 'Online'),
    (27, 16, '202', 'R', '11:00:00', '11:50:00', 'Online'),
    (27, 16, '203', 'R', '12:00:00', '12:50:00', 'Online'),
    (27, 16, '205', 'R', '14:00:00', '14:50:00', 'Online'),
    (28, 16, '001', 'TR', '08:30:00', '09:45:00', 'Online'),
    (28, 16, '002', 'TR', '16:00:00', '17:15:00', 'Online'),
    -- Summer 2021 (semester 17)
    (1, 17, '0U1', 'MW', '12:30:00', '14:45:00', 'Online'),
    (1, 17, '0U2', 'MW', '15:00:00', '17:15:00', 'Online'),
    (2, 17, '0W1', 'MW', '17:30:00', '19:45:00', 'Online'),
    (2, 17, '0W2', NULL, NULL, NULL, 'Online'),
    (2, 17, '0W3', 'S', '13:00:00', '17:00:00', 'Online'),
    (3, 17, '0U1', 'TR', '10:00:00', '12:15:00', 'Online'),
    (3, 17, '0U2', 'MW', '10:00:00', '12:15:00', 'Online'),
    (3, 17, '0W1', 'TR', '17:30:00', '19:45:00', 'Online'),
    (5, 17, '0U1', 'TR', '10:00:00', '12:15:00', 'Online'),
    (5, 17, '0U2', 'MW', '13:00:00', '15:15:00', 'Online'),
    (6, 17, '0U1', 'MW', '12:30:00', '14:45:00', 'Online'),
    (8, 17, '0U1', 'TR', '15:00:00', '17:15:00', 'Online'),
    (8, 17, '5U1', 'TR', '17:30:00', '19:45:00', 'Online'),
    (13, 17, '0W1', 'MWF', '14:00:00', '16:00:00', 'Online'),
    (21, 17, '5U1', 'T', '18:00:00', '22:00:00', 'JSOM 1.212'),
    (22, 17, '5U1', 'R', '18:00:00', '22:00:00', 'JSOM 1.212'),
    (24, 17, '0U1', 'MW', '10:00:00', '12:15:00', 'JSOM 1.217'),
    (25, 17, '0W1', NULL, NULL, NULL, 'Online'),
    -- Fall 2021 (semester 18)
    (1, 18, '001', 'MW', '14:30:00', '15:45:00', 'FN 2.104'),
    (1, 18, '002', 'TR', '13:00:00', '14:15:00', 'ECSS 2.305'),
    (1, 18, '003', 'TR', '16:00:00', '17:15:00', 'Online'),
    (1, 18, '501', 'MW', '17:30:00', '18:45:00', 'CB3 1.312'),
    (1, 18, '502', 'TR', '17:30:00', '18:45:00', 'JSOM 2.804'),
    (1, 18, '503', 'MW', '17:30:00', '18:45:00', 'SCI 2.235'),
    (2, 18, '001', 'MW', '14:30:00', '15:45:00', 'JO 4.102'),
    (2, 18, '002', 'TR', '10:00:00', '11:15:00', 'ECSS 2.311'),
    (2, 18, '003', 'TR', '10:00:00', '11:15:00', 'ECSS 2.410'),
    (2, 18, '004', 'MW', '08:30:00', '09:45:00', 'ECSS 2.305'),
    (2, 18, '005', 'MW', '11:30:00', '12:45:00', 'ECSS 2.412'),
    (2, 18, '501', 'TR', '20:30:00', '21:45:00', 'ECSW 1.355'),
    (2, 18, '502', 'TR', '17:30:00', '18:45:00', 'ECSW 1.365'),
    (2, 18, '503', 'TR', '19:00:00', '20:15:00', 'ECSW 3.210'),
    (2, 18, '504', 'TR', '17:30:00', '18:45:00', 'ECSS 2.311'),
    (3, 18, '001', 'TR', '08:30:00', '09:45:00', 'JSOM 12.202'),
    (3, 18, '002', 'MW', '16:00:00', '17:15:00', 'ECSS 2.305'),
    (3, 18, '003', 'MW', '14:30:00', '15:45:00', 'GR 2.302'),
    (3, 18, '004', 'MW', '08:30:00', '09:45:00', 'ECSW 3.210'),
    (3, 18, '005', 'TR', '08:30:00', '09:45:00', 'ECSS 2.203'),
    (3, 18, '006', 'MW', '13:00:00', '14:15:00', 'ECSS 2.311'),
    (3, 18, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.410'),
    (3, 18, '502', 'TR', '19:00:00', '20:15:00', 'ECSS 2.410'),
    (3, 18, '504', 'TR', '19:00:00', '20:15:00', 'ECSS 2.305'),
    (3, 18, 'HON', 'TR', '17:30:00', '18:45:00', 'CB3 1.306'),
    (4, 18, '001', 'F', '16:00:00', '18:45:00', 'ECSW 1.315'),
    (4, 18, '002', 'F', '16:00:00', '18:45:00', 'SCI 1.220'),
    (5, 18, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.203'),
    (5, 18, '002', 'TR', '14:30:00', '15:45:00', 'ECSS 2.201'),
    (5, 18, '003', 'TR', '16:00:00', '17:15:00', 'GR 3.302'),
    (5, 18, '004', 'MW', '13:00:00', '14:15:00', 'ECSS 2.201'),
    (5, 18, '005', 'TR', '16:00:00', '17:15:00', 'AD 2.232'),
    (5, 18, '201', NULL, NULL, NULL, NULL),
    (5, 18, '203', NULL, NULL, NULL, NULL),
    (5, 18, '205', NULL, NULL, NULL, NULL),
    (5, 18, '207', NULL, NULL, NULL, NULL),
    (5, 18, '209', NULL, NULL, NULL, NULL),
    (5, 18, '211', NULL, NULL, NULL, NULL),
    (5, 18, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.415'),
    (6, 18, '001', 'TR', '16:00:00', '17:15:00', 'SLC 1.102'),
    (6, 18, '501', 'TR', '17:30:00', '18:45:00', 'JO 3.516'),
    (7, 18, '001', 'TR', '10:00:00', '11:15:00', 'CB3 1.312'),
    (7, 18, '002', 'TR', '11:30:00', '12:45:00', 'FO 1.502'),
    (8, 18, '001', 'MW', '14:30:00', '15:45:00', 'ECSS 2.201'),
    (8, 18, '002', 'MW', '08:30:00', '09:45:00', 'JSOM 2.902'),
    (9, 18, '001', 'TR', '11:30:00', '12:45:00', 'ECSW 1.355'),
    (9, 18, '0W1', NULL, NULL, NULL, 'Online'),
    (9, 18, '0W2', NULL, NULL, NULL, 'Online'),
    (9, 18, '0W3', NULL, NULL, NULL, 'Online'),
    (10, 18, '001', 'MW', '13:00:00', '14:15:00', 'ECSS 2.412'),
    (10, 18, '501', 'MW', '17:30:00', '18:45:00', 'ECSW 1.355'),
    (11, 18, '001', 'TR', '10:00:00', '11:15:00', 'CB 1.218'),
    (11, 18, '501', 'TR', '17:30:00', '18:45:00', 'SCI 2.215'),
    (12, 18, '001', 'TR', '10:00:00', '11:15:00', 'ECSW 1.365'),
    (13, 18, '001', 'TR', '13:00:00', '14:45:00', 'SCI 3.230'),
    (15, 18, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 18, '001', 'MWF', '09:00:00', '09:50:00', 'SCI 1.210'),
    (17, 18, '001', 'MWF', '11:00:00', '11:50:00', 'SCI 1.210'),
    (18, 18, '001', 'MW', '11:30:00', '12:45:00', 'SLC 2.304'),
    (18, 18, '002', 'TR', '11:30:00', '12:45:00', 'SLC 2.304'),
    (18, 18, '003', 'MW', '16:00:00', '17:15:00', 'SLC 2.304'),
    (18, 18, '004', 'TR', '08:30:00', '09:45:00', 'SLC 2.304'),
    (19, 18, '001', 'TR', '08:30:00', '09:45:00', 'SLC 1.102'),
    (20, 18, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 18, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 18, '001', 'TR', '10:00:00', '11:15:00', 'JSOM 1.110'),
    (21, 18, '002', 'TR', '11:30:00', '12:45:00', 'JSOM 1.110'),
    (22, 18, '001', 'MW', '13:00:00', '14:15:00', 'JSOM 1.212'),
    (22, 18, '501', 'W', '19:00:00', '21:45:00', 'JSOM 2.801'),
    (23, 18, '001', 'M', '13:00:00', '15:45:00', 'JSOM 2.902'),
    (23, 18, '002', 'M', '10:00:00', '12:45:00', 'JSOM 2.902'),
    (24, 18, '001', 'MW', '10:00:00', '11:15:00', 'JSOM 12.214'),
    (24, 18, '002', 'TR', '10:00:00', '11:15:00', 'JSOM 11.210'),
    (25, 18, '001', 'TR', '13:00:00', '14:15:00', 'ATC 2.602'),
    (25, 18, '002', 'TR', '14:30:00', '15:45:00', 'ATC 2.602'),
    (25, 18, '003', 'TR', '11:30:00', '12:45:00', 'ATC 2.602'),
    (26, 18, '001', 'TR', '14:30:00', '15:45:00', 'ATC 2.101'),
    (26, 18, '002', 'TR', '16:00:00', '17:15:00', 'ATC 2.101'),
    (28, 18, '001', 'MW', '10:00:00', '11:15:00', 'CB3 1.302'),
    -- Spring 2022 (semester 19)
    (1, 19, '001', 'MWF', '15:00:00', '15:50:00', 'HH 2.402'),
    (1, 19, '002', 'MW', '10:00:00', '11:15:00', 'ECSS 2.312'),
    (1, 19, '004', 'MW', '11:30:00', '12:45:00', 'ECSS 2.312'),
    (1, 19, '005', 'MW', '08:30:00', '09:45:00', 'ECSS 2.312'),
    (1, 19, '006', 'MW', '10:00:00', '11:15:00', 'JO 4.614'),
    (1, 19, '007', 'TR', '13:00:00', '14:15:00', 'ECSS 2.312'),
    (1, 19, '008', 'TR', '10:00:00', '11:15:00', 'ECSS 2.312'),
    (1, 19, '009', 'MWF', '12:00:00', '12:50:00', 'SLC 1.102'),
    (1, 19, '010', 'MW', '11:30:00', '12:45:00', 'ECSS 2.201'),
    (1, 19, '012', 'MW', '13:00:00', '14:15:00', 'ECSS 2.312'),
    (1, 19, '014', 'TR', '16:00:00', '17:15:00', 'ECSS 2.203'),
    (1, 19, '501', 'MW', '17:30:00', '18:45:00', 'ECSS 2.312'),
    (2, 19, '001', 'MW', '10:00:00', '11:15:00', 'ECSS 2.410'),
    (2, 19, '002', 'MW', '17:30:00', '18:45:00', 'ECSW 1.355'),
    (2, 19, '003', 'TR', '11:30:00', '12:45:00', 'ECSS 2.410'),
    (2, 19, '501', 'MW', '19:00:00', '20:15:00', 'GR 2.302'),
    (3, 19, '001', 'MW', '16:00:00', '17:15:00', 'ECSS 2.412'),
    (3, 19, '002', 'TR', '16:00:00', '17:15:00', 'ECSW 3.210'),
    (3, 19, '003', 'MW', '14:30:00', '15:45:00', 'MC 2.410'),
    (3, 19, '004', 'MW', '08:30:00', '09:45:00', 'ECSS 2.415'),
    (3, 19, '006', 'MW', '10:00:00', '11:15:00', 'HH 2.502'),
    (3, 19, '007', 'MW', '13:00:00', '14:15:00', 'JO 4.102'),
    (3, 19, '501', 'TR', '19:00:00', '20:15:00', 'ECSS 2.306'),
    (3, 19, '502', 'TR', '17:30:00', '18:45:00', 'ECSS 2.410'),
    (3, 19, '503', 'TR', '17:30:00', '18:45:00', 'GR 2.530'),
    (4, 19, '001', 'F', '16:00:00', '18:45:00', 'ECSW 1.315'),
    (4, 19, '002', 'F', '16:00:00', '18:45:00', 'FN 2.102'),
    (5, 19, '001', 'TR', '10:00:00', '11:15:00', 'ECSS 2.412'),
    (5, 19, '002', 'TR', '08:30:00', '09:45:00', 'ECSS 2.415'),
    (5, 19, '003', 'TR', '13:00:00', '14:15:00', 'ECSS 2.311'),
    (5, 19, '004', 'MW', '16:00:00', '17:15:00', 'GR 4.301'),
    (5, 19, '501', 'MW', '17:30:00', '18:45:00', 'JO 4.102'),
    (6, 19, '001', 'TR', '16:00:00', '17:15:00', 'FN 2.102'),
    (6, 19, '002', 'TR', '11:30:00', '12:45:00', 'ECSN 2.112'),
    (7, 19, '001', 'TR', '10:00:00', '11:15:00', 'SCI 2.210'),
    (7, 19, '002', 'TR', '11:30:00', '12:45:00', 'SCI 2.225'),
    (8, 19, '001', 'TR', '10:00:00', '11:15:00', 'FO 1.502'),
    (8, 19, '002', 'MW', '13:00:00', '14:15:00', 'JSOM 2.904'),
    (8, 19, '003', 'MW', '16:00:00', '17:15:00', 'JSOM 2.107'),
    (9, 19, '001', 'MW', '16:00:00', '17:15:00', 'ECSW 1.365'),
    (9, 19, '0W1', NULL, NULL, NULL, 'Online'),
    (9, 19, '0W2', NULL, NULL, NULL, 'Online'),
    (10, 19, '001', 'MW', '11:30:00', '12:45:00', 'ECSW 1.355'),
    (11, 19, '001', 'MW', '14:30:00', '15:45:00', 'SLC 2.302'),
    (12, 19, '001', 'TR', '10:00:00', '11:15:00', 'ECSW 3.250'),
    (12, 19, '002', 'MW', '11:30:00', '12:45:00', 'JSOM 2.717'),
    (13, 19, '001', 'TR', '13:00:00', '14:45:00', 'SCI 3.230'),
    (14, 19, '501', 'TR', '17:30:00', '18:45:00', 'SCI 3.230'),
    (14, 19, '801', 'W', '17:30:00', '20:15:00', 'CB 1.223'),
    (14, 19, '802', 'F', '17:30:00', '20:15:00', 'CB 1.102'),
    (14, 19, '803', 'M', '17:30:00', '20:15:00', 'CB 1.223'),
    (15, 19, '001', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 19, '001', 'MWF', '13:00:00', '13:50:00', 'SLC 2.303'),
    (17, 19, '001', 'MWF', '09:00:00', '09:50:00', 'SLC 2.303'),
    (18, 19, '001', 'MW', '08:30:00', '09:45:00', 'SLC 2.203'),
    (18, 19, '002', 'MW', '11:30:00', '12:45:00', 'SLC 2.203'),
    (18, 19, '003', 'TR', '16:00:00', '17:15:00', 'SLC 2.203'),
    (19, 19, '001', 'MWF', '09:00:00', '09:50:00', 'JSOM 12.206'),
    (20, 19, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 19, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 19, '001', 'TR', '10:00:00', '11:15:00', 'SOM 1.102'),
    (21, 19, '501', 'M', '19:00:00', '21:45:00', 'JSOM 2.112'),
    (22, 19, '001', 'MW', '08:30:00', '09:45:00', 'JSOM 2.106'),
    (22, 19, '501', 'W', '19:00:00', '21:45:00', 'JSOM 2.112'),
    (23, 19, '001', 'M', '13:00:00', '15:45:00', 'JSOM 11.206'),
    (23, 19, '002', 'M', '16:00:00', '18:45:00', 'JSOM 11.206'),
    (24, 19, '001', 'MW', '14:30:00', '15:45:00', 'JSOM 12.222'),
    (24, 19, '002', 'TR', '10:00:00', '11:15:00', 'JSOM 2.722'),
    (25, 19, '001', 'TR', '10:00:00', '11:15:00', 'ATC 2.602'),
    (25, 19, '002', 'TR', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 19, '003', 'TR', '13:00:00', '14:15:00', 'ATC 2.602'),
    (26, 19, '001', 'MW', '10:00:00', '11:15:00', 'ATC 2.918'),
    (26, 19, '002', 'MW', '11:30:00', '12:45:00', 'ATC 2.918'),
    (27, 19, '001', 'MW', '16:00:00', '16:50:00', 'SCI 1.220'),
    (27, 19, '203', 'R', '10:00:00', '10:50:00', 'CB 1.222'),
    (27, 19, '204', 'R', '13:00:00', '13:50:00', 'CB3 1.308'),
    (27, 19, '205', 'R', '16:00:00', '16:50:00', 'JO 4.502'),
    (27, 19, '207', 'R', '15:00:00', '15:50:00', 'CB 1.219'),
    (27, 19, '208', 'F', '12:00:00', '12:50:00', 'JO 4.102'),
    (27, 19, '210', 'F', '13:00:00', '13:50:00', 'SLC 2.302'),
    (28, 19, '001', 'TR', '11:30:00', '12:45:00', 'CB 1.219'),
    (28, 19, '501', 'M', '19:00:00', '21:45:00', 'JO 4.708'),
    -- Summer 2022 (semester 20)
    (1, 20, '0U1', 'MW', '12:30:00', '14:45:00', 'ECSN 2.120'),
    (1, 20, '5U1', 'TR', '17:30:00', '19:45:00', 'ECSS 2.311'),
    (2, 20, '0U1', 'MW', '12:30:00', '14:45:00', 'ECSS 2.201'),
    (2, 20, '5U1', 'TR', '17:30:00', '19:45:00', 'ECSS 2.203'),
    (3, 20, '0U1', 'TR', '10:00:00', '12:15:00', 'JSOM 11.202'),
    (3, 20, '0U2', 'TR', '15:00:00', '17:15:00', 'ECSS 2.306'),
    (5, 20, '0U1', 'TR', '10:00:00', '12:15:00', 'SCI 1.220'),
    (6, 20, '0U1', 'TR', '12:30:00', '14:45:00', 'SCI 3.230'),
    (8, 20, '0U1', 'TR', '15:00:00', '17:15:00', 'JSOM 12.206'),
    (8, 20, '5U1', 'TR', '17:30:00', '19:45:00', 'ECSS 2.306'),
    (9, 20, '051', 'MTWR', '10:00:00', '12:15:00', 'SCI 2.230'),
    (10, 20, '0W1', 'TR', '10:00:00', '12:15:00', 'Online'),
    (13, 20, '0U1', 'MWF', '14:00:00', '16:00:00', 'SCI 3.220'),
    (18, 20, '0U1', 'TR', '11:30:00', '12:45:00', 'SLC 3.102'),
    (21, 20, '5U1', 'T', '18:00:00', '22:00:00', 'JSOM 1.117'),
    (22, 20, '5U1', 'R', '18:00:00', '22:00:00', 'JSOM 12.218'),
    (25, 20, '551', 'MW', '17:30:00', '21:45:00', 'ATC 2.602'),
    (26, 20, '0U1', 'TR', '12:30:00', '14:45:00', 'ATC 2.602'),
    -- Fall 2022 (semester 1)
    (6, 1, '001', 'TR', '16:00:00', '17:15:00', 'JO 3.516'),
    (6, 1, '501', 'TR', '17:30:00', '18:45:00', 'GR 3.302'),
    (7, 1, '001', 'TR', '10:00:00', '11:15:00', 'SCI 2.235'),
    (7, 1, '002', 'TR', '11:30:00', '12:45:00', 'SCI 2.235'),
    (8, 1, '001', 'MW', '14:30:00', '15:45:00', 'ECSS 2.201'),
    (8, 1, '002', 'MW', '08:30:00', '09:45:00', 'JSOM 2.902'),
    (9, 1, '001', 'TR', '11:30:00', '12:45:00', 'ECSW 1.355'),
    (9, 1, '002', 'M', '14:30:00', '15:45:00', 'ECSW 3.250'),
    (10, 1, '001', 'MW', '13:00:00', '14:15:00', 'ECSW 3.250'),
    (10, 1, '501', 'MW', '17:30:00', '18:45:00', 'SLC 2.303'),
    (11, 1, '001', 'TR', '10:00:00', '11:15:00', 'FN 2.104'),
    (11, 1, '002', 'MW', '08:30:00', '09:45:00', 'JSOM 11.210'),
    (12, 1, '001', 'TR', '10:00:00', '11:15:00', 'ECSW 1.365'),
    (13, 1, '001', 'TR', '13:00:00', '14:45:00', 'SCI 3.230'),
    (15, 1, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 1, '001', 'MWF', '11:00:00', '11:50:00', 'SCI 1.210'),
    (17, 1, '001', 'MWF', '09:00:00', '09:50:00', 'SCI 1.210'),
    (18, 1, '001', 'MW', '11:30:00', '12:45:00', 'SLC 2.203'),
    (18, 1, '002', 'TR', '11:30:00', '12:45:00', 'SLC 2.203'),
    (18, 1, '004', 'TR', '08:30:00', '09:45:00', 'SLC 2.203'),
    (18, 1, '005', 'MW', '08:30:00', '09:45:00', 'SLC 2.203'),
    (19, 1, '001', 'TR', '08:30:00', '09:45:00', 'SLC 1.102'),
    (20, 1, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 1, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 1, '002', 'TR', '11:30:00', '12:45:00', 'JSOM 11.206'),
    (21, 1, '501', 'M', '19:00:00', '21:45:00', 'JSOM 2.717'),
    (22, 1, '001', 'MW', '13:00:00', '14:15:00', 'JSOM 1.212'),
    (22, 1, '501', 'W', '19:00:00', '21:45:00', 'JSOM 2.902'),
    (23, 1, '001', 'M', '13:00:00', '15:45:00', 'JSOM 2.901'),
    (23, 1, '002', 'M', '10:00:00', '12:45:00', 'JSOM 2.902'),
    (24, 1, '001', 'MW', '10:00:00', '11:15:00', 'JSOM 12.214'),
    (24, 1, '002', 'TR', '10:00:00', '11:15:00', 'JSOM 11.210'),
    (25, 1, '001', 'TR', '13:00:00', '14:15:00', 'ATC 2.602'),
    (25, 1, '002', 'TR', '14:30:00', '15:45:00', 'ATC 2.602'),
    (25, 1, '003', 'TR', '11:30:00', '12:45:00', 'ATC 2.602'),
    (26, 1, '001', 'TR', '14:30:00', '15:45:00', 'ATC 2.101'),
    (26, 1, '002', 'TR', '16:00:00', '17:15:00', 'ATC 2.101'),
    (26, 1, '003', 'MW', '14:30:00', '15:45:00', 'ATC 2.918'),
    (26, 1, '004', 'MW', '16:00:00', '17:15:00', 'ATC 3.605'),
    (27, 1, '001', 'MW', '12:00:00', '12:50:00', 'FN 2.102'),
    (27, 1, '201', 'R', '08:00:00', '08:50:00', 'CB 1.222'),
    (27, 1, '202', 'R', '11:00:00', '11:50:00', 'CB 1.222'),
    (27, 1, '203', 'R', '12:00:00', '12:50:00', 'CB 1.222'),
    (27, 1, '204', 'F', '10:00:00', '10:50:00', 'FN 2.106'),
    (27, 1, '205', 'F', '14:00:00', '14:50:00', 'FN 2.106'),
    (27, 1, '206', 'F', '12:00:00', '12:50:00', 'FN 2.106'),
    (28, 1, '001', 'MW', '16:00:00', '17:15:00', 'JO 4.708'),
    (28, 1, '002', 'TR', '11:30:00', '12:45:00', 'JSOM 2.804'),
    -- Spring 2023 (semester 2)
    (6, 2, '001', 'TR', '16:00:00', '17:15:00', 'GR 3.302'),
    (6, 2, '002', 'TR', '11:30:00', '12:45:00', 'SCI 2.210'),
    (7, 2, '001', 'TR', '10:00:00', '11:15:00', 'SCI 2.210'),
    (7, 2, '002', 'TR', '11:30:00', '12:45:00', 'SCI 2.225'),
    (8, 2, '001', 'TR', '16:00:00', '17:15:00', 'FO 2.702'),
    (8, 2, '002', 'MW', '13:00:00', '14:15:00', 'AD 2.232'),
    (8, 2, '003', 'MW', '16:00:00', '17:15:00', 'FO 2.702'),
    (9, 2, '001', 'MW', '10:00:00', '11:15:00', 'ECSW 1.355'),
    (9, 2, '002', 'MW', '16:00:00', '17:15:00', 'ECSW 1.355'),
    (10, 2, '001', 'TR', '13:00:00', '14:15:00', 'ECSW 1.365'),
    (11, 2, '001', 'MW', '14:30:00', '15:45:00', 'ECSN 2.110'),
    (12, 2, '001', 'TR', '08:30:00', '09:45:00', 'JSOM 2.801'),
    (12, 2, '002', 'MW', '11:30:00', '12:45:00', 'ECSW 3.250'),
    (13, 2, '001', 'TR', '13:00:00', '14:45:00', 'SCI 3.230'),
    (14, 2, '001', 'MW', '13:00:00', '14:15:00', 'SCI 2.225'),
    (14, 2, '301', 'M', '14:30:00', '17:15:00', 'CB 1.223'),
    (14, 2, '302', 'W', '14:30:00', '17:15:00', 'CB 1.223'),
    (14, 2, '303', 'F', '13:00:00', '15:45:00', 'CB 1.223'),
    (15, 2, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 2, '001', 'MWF', '13:00:00', '13:50:00', 'SLC 2.303'),
    (17, 2, '001', 'MWF', '09:00:00', '09:50:00', 'SLC 2.303'),
    (18, 2, '001', 'MW', '08:30:00', '09:45:00', 'SLC 2.203'),
    (18, 2, '002', 'MW', '11:30:00', '12:45:00', 'SLC 2.203'),
    (18, 2, '003', 'TR', '16:00:00', '17:15:00', 'SLC 2.203'),
    (19, 2, '001', 'TR', '11:30:00', '12:45:00', 'JO 4.102'),
    (20, 2, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 2, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 2, '501', 'M', '19:00:00', '21:45:00', 'JSOM 12.206'),
    (22, 2, '001', 'MW', '08:30:00', '09:45:00', 'JSOM 2.115'),
    (22, 2, '501', 'W', '19:00:00', '21:45:00', 'JSOM 2.112'),
    (23, 2, '001', 'M', '13:00:00', '15:45:00', 'JSOM 11.206'),
    (23, 2, '002', 'M', '16:00:00', '18:45:00', 'JSOM 2.802'),
    (24, 2, '001', 'MW', '14:30:00', '15:45:00', 'JSOM 2.112'),
    (24, 2, '002', 'TR', '10:00:00', '11:15:00', 'JSOM 12.218'),
    (25, 2, '001', 'TR', '14:30:00', '15:45:00', 'ATC 2.602'),
    (25, 2, '002', 'TR', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 2, '003', 'TR', '13:00:00', '14:15:00', 'ATC 2.602'),
    (26, 2, '001', 'MW', '10:00:00', '11:15:00', 'ATC 2.918'),
    (26, 2, '002', 'MW', '11:30:00', '12:45:00', 'ATC 2.918'),
    (26, 2, '003', 'TR', '11:30:00', '12:45:00', 'ATC 2.101'),
    (26, 2, '501', 'MW', '19:00:00', '20:15:00', 'ATC 2.101'),
    (27, 2, '001', 'TR', '12:00:00', '12:50:00', 'JO 2.604'),
    (27, 2, '201', 'R', '15:00:00', '15:50:00', 'SCI 3.270'),
    (27, 2, '202', 'R', '16:00:00', '16:50:00', 'CB 1.218'),
    (27, 2, '204', 'F', '10:00:00', '10:50:00', 'FN 2.104'),
    (27, 2, '205', 'F', '12:00:00', '12:50:00', 'CB 1.218'),
    (28, 2, '001', 'TR', '10:00:00', '11:15:00', 'JO 4.708'),
    (28, 2, '501', 'W', '19:00:00', '21:45:00', 'JO 4.708'),
    -- Summer 2023 (semester 3)
    (6, 3, '0U1', 'TR', '12:30:00', '14:45:00', 'SCI 3.230'),
    (8, 3, '5U1', 'TR', '17:30:00', '19:45:00', 'ECSS 2.306'),
    (9, 3, '051', 'MTWR', '10:00:00', '12:15:00', 'SCI 2.230'),
    (10, 3, '0W1', 'TR', '12:30:00', '14:45:00', 'Online'),
    (13, 3, '0U1', 'MWF', '14:00:00', '16:00:00', 'SCI 3.220'),
    (18, 3, '0U1', 'TR', '13:30:00', '14:45:00', 'SCI 3.220'),
    (19, 3, '0U1', 'MW', '10:00:00', '12:15:00', 'JSOM 2.106'),
    (21, 3, '5U1', 'T', '18:00:00', '22:00:00', 'JSOM 12.202'),
    (22, 3, '5U1', 'R', '18:00:00', '22:00:00', 'JSOM 12.218'),
    (25, 3, '551', 'MW', '17:30:00', '21:45:00', 'ATC 2.918'),
    (26, 3, '0W1', NULL, NULL, NULL, 'Online'),
    -- Fall 2023 (semester 4)
    (6, 4, '001', 'TR', '16:00:00', '17:15:00', 'FN 2.214'),
    (6, 4, '501', 'TR', '17:30:00', '18:45:00', 'GR 3.302'),
    (7, 4, '001', 'TR', '10:00:00', '11:15:00', 'SCI 2.235'),
    (7, 4, '002', 'TR', '11:30:00', '12:45:00', 'SCI 2.235'),
    (8, 4, '001', 'TR', '14:30:00', '15:45:00', 'HH 2.502'),
    (8, 4, '002', 'MW', '08:30:00', '09:45:00', 'HH 2.502'),
    (9, 4, '001', 'TR', '11:30:00', '12:45:00', 'ECSW 3.250'),
    (9, 4, '002', 'MW', '08:30:00', '09:45:00', 'ECSW 1.355'),
    (10, 4, '001', 'MW', '11:30:00', '12:45:00', 'ECSW 1.355'),
    (10, 4, '501', 'MW', '17:30:00', '18:45:00', 'ECSW 3.250'),
    (11, 4, '001', 'TR', '10:00:00', '11:15:00', 'FO 2.404'),
    (11, 4, '002', 'MW', '14:30:00', '15:45:00', 'FO 2.404'),
    (12, 4, '001', 'TR', '08:30:00', '09:45:00', 'GR 2.302'),
    (12, 4, '002', 'TR', '14:30:00', '15:45:00', 'CB 1.222'),
    (13, 4, '001', 'TR', '13:00:00', '14:45:00', 'SCI 3.230'),
    (15, 4, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 4, '001', 'MWF', '11:00:00', '11:50:00', 'SCI 1.210'),
    (17, 4, '001', 'MWF', '09:00:00', '09:50:00', 'SCI 1.210'),
    (18, 4, '001', 'MW', '11:30:00', '12:45:00', 'SLC 2.203'),
    (18, 4, '002', 'TR', '11:30:00', '12:45:00', 'SLC 2.203'),
    (18, 4, '004', 'TR', '08:30:00', '09:45:00', 'SLC 2.203'),
    (19, 4, '001', 'TR', '08:30:00', '09:45:00', 'SLC 1.102'),
    (20, 4, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 4, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 4, '001', 'TR', '11:30:00', '12:45:00', 'JSOM 2.103'),
    (21, 4, '501', 'M', '19:00:00', '21:45:00', 'JSOM 12.202'),
    (22, 4, '001', 'MW', '13:00:00', '14:15:00', 'JSOM 11.206'),
    (22, 4, '501', 'W', '19:00:00', '21:45:00', 'JSOM 2.902'),
    (23, 4, '001', 'M', '13:00:00', '15:45:00', 'JSOM 2.901'),
    (23, 4, '002', 'M', '10:00:00', '12:45:00', 'JSOM 2.902'),
    (24, 4, '001', 'MW', '10:00:00', '11:15:00', 'JSOM 12.214'),
    (24, 4, '002', 'TR', '10:00:00', '11:15:00', 'JSOM 11.210'),
    (25, 4, '0H1', 'T', '13:00:00', '14:15:00', 'ATC 2.602'),
    (25, 4, '0H2', 'T', '14:30:00', '15:45:00', 'ATC 2.602'),
    (25, 4, '0H3', 'T', '11:30:00', '12:45:00', 'ATC 2.602'),
    (26, 4, '001', 'TR', '14:30:00', '15:45:00', 'ATC 2.101'),
    (26, 4, '002', 'TR', '16:00:00', '17:15:00', 'ATC 2.101'),
    (26, 4, '003', 'MW', '14:30:00', '15:45:00', 'ATC 2.918'),
    (28, 4, '001', 'MW', '16:00:00', '17:15:00', 'CB 1.222'),
    -- Spring 2024 (semester 5)
    (6, 5, '001', 'TR', '16:00:00', '17:15:00', 'CR 1.202'),
    (6, 5, '002', 'TR', '11:30:00', '12:45:00', 'SCI 2.210'),
    (7, 5, '001', 'TR', '10:00:00', '11:15:00', 'SCI 2.210'),
    (7, 5, '002', 'TR', '11:30:00', '12:45:00', 'SCI 2.225'),
    (8, 5, '001', 'TR', '16:00:00', '17:15:00', 'FO 2.404'),
    (8, 5, '002', 'MW', '13:00:00', '14:15:00', 'ECSS 2.201'),
    (8, 5, '003', 'MW', '16:00:00', '17:15:00', 'JO 4.102'),
    (9, 5, '001', 'MW', '16:00:00', '17:15:00', 'SCI 2.230'),
    (9, 5, '0H1', 'W', '10:00:00', '11:15:00', 'HH 2.502'),
    (10, 5, '001', 'TR', '13:00:00', '14:15:00', 'ECSW 1.365'),
    (11, 5, '001', 'MW', '14:30:00', '15:45:00', 'FO 2.702'),
    (12, 5, '001', 'TR', '08:30:00', '09:45:00', 'AD 2.216'),
    (12, 5, '002', 'MW', '11:30:00', '12:45:00', 'ECSS 2.201'),
    (13, 5, '001', 'TR', '13:00:00', '14:45:00', 'SCI 2.225'),
    (14, 5, '001', 'MW', '13:00:00', '14:15:00', 'SCI 2.225'),
    (14, 5, '301', 'M', '14:30:00', '17:15:00', 'SCI 2.220'),
    (14, 5, '302', 'W', '14:30:00', '17:15:00', 'SCI 2.220'),
    (14, 5, '303', 'F', '13:00:00', '15:45:00', 'SCI 2.220'),
    (15, 5, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 5, '001', 'MWF', '13:00:00', '13:50:00', 'JO 3.516'),
    (17, 5, '001', 'MWF', '09:00:00', '09:50:00', 'SLC 2.303'),
    (18, 5, '001', 'MW', '08:30:00', '09:45:00', 'SLC 3.102'),
    (18, 5, '002', 'MW', '11:30:00', '12:45:00', 'SLC 2.304'),
    (18, 5, '003', 'TR', '16:00:00', '17:15:00', 'FO 1.202'),
    (19, 5, '001', 'TR', '11:30:00', '12:45:00', 'SLC 1.102'),
    (20, 5, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 5, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 5, '501', 'M', '19:00:00', '21:45:00', 'JSOM 12.206'),
    (22, 5, '001', 'MW', '08:30:00', '09:45:00', 'JSOM 2.115'),
    (22, 5, '501', 'W', '19:00:00', '21:45:00', 'JSOM 2.112'),
    (23, 5, '001', 'M', '13:00:00', '15:45:00', 'JSOM 2.803'),
    (23, 5, '002', 'M', '16:00:00', '18:45:00', 'JSOM 2.802'),
    (24, 5, '001', 'MW', '14:30:00', '15:45:00', 'JSOM 2.112'),
    (24, 5, '002', 'TR', '10:00:00', '11:15:00', 'JSOM 12.218'),
    (25, 5, '0H1', 'T', '14:30:00', '15:45:00', 'ATC 2.602'),
    (25, 5, '0H3', 'T', '13:00:00', '14:15:00', 'ATC 2.602'),
    (25, 5, '0W1', NULL, NULL, NULL, 'Online'),
    (26, 5, '001', 'MW', '10:00:00', '11:15:00', 'ATC 2.918'),
    (26, 5, '002', 'MW', '11:30:00', '12:45:00', 'ATC 2.918'),
    (26, 5, '003', 'TR', '11:30:00', '12:45:00', 'ATC 2.101'),
    (26, 5, '501', 'MW', '19:00:00', '20:15:00', 'ATC 2.101'),
    (27, 5, '001', 'TR', '13:00:00', '13:50:00', 'SLC 1.102'),
    (27, 5, '201', 'R', '14:00:00', '14:50:00', 'ATC 2.602'),
    (27, 5, '202', 'R', '15:00:00', '15:50:00', 'ATC 2.602'),
    (27, 5, '203', 'F', '08:00:00', '08:50:00', 'FN 2.104'),
    (27, 5, '204', 'F', '09:00:00', '09:50:00', 'FN 2.104'),
    (27, 5, '205', 'F', '10:00:00', '10:50:00', 'JO 4.502'),
    (27, 5, '206', 'F', '11:00:00', '11:50:00', 'JO 4.502'),
    (27, 5, '207', 'F', '12:00:00', '12:50:00', 'FN 2.202'),
    (27, 5, '208', 'F', '13:00:00', '13:50:00', 'FN 2.202'),
    (28, 5, '001', 'TR', '14:30:00', '15:45:00', 'JO 4.708'),
    (28, 5, '501', 'W', '19:00:00', '21:45:00', 'JO 4.708'),
    -- Summer 2024 (semester 6)
    (6, 6, '0U1', 'TR', '12:30:00', '14:45:00', 'FO 2.702'),
    (8, 6, '5U1', 'TR', '17:30:00', '19:45:00', 'ECSS 2.306'),
    (10, 6, '0W1', 'TR', '12:30:00', '14:45:00', 'Online'),
    (13, 6, '0U1', 'MWF', '14:00:00', '16:00:00', 'SCI 3.220'),
    (19, 6, '0U1', 'MW', '10:00:00', '12:15:00', 'JSOM 2.106'),
    (21, 6, '5U1', 'T', '18:00:00', '21:45:00', 'JSOM 12.202'),
    (22, 6, '5U1', 'M', '18:00:00', '22:00:00', 'JSOM 12.210'),
    (25, 6, '551', 'MW', '18:00:00', '22:00:00', 'ATC 2.918'),
    (26, 6, '0W1', NULL, NULL, NULL, 'Online'),
    -- Fall 2024 (semester 7)
    (6, 7, '001', 'TR', '16:00:00', '17:15:00', 'SCI 2.210'),
    (6, 7, '501', 'TR', '17:30:00', '18:45:00', 'FO 2.208'),
    (7, 7, '001', 'TR', '10:00:00', '11:15:00', 'SCI 3.260'),
    (7, 7, '002', 'TR', '11:30:00', '12:45:00', 'SCI 3.260'),
    (8, 7, '001', 'TR', '14:30:00', '15:45:00', 'FO 2.404'),
    (8, 7, '002', 'MW', '08:30:00', '09:45:00', 'ECSS 2.412'),
    (9, 7, '001', 'TR', '11:30:00', '12:45:00', 'HH 2.502'),
    (9, 7, '0H1', 'MW', '13:00:00', '14:15:00', 'FO 2.404'),
    (10, 7, '001', 'MW', '14:30:00', '15:45:00', 'GR 2.302'),
    (10, 7, '501', 'MW', '17:30:00', '18:45:00', 'HH 2.502'),
    (11, 7, '001', 'TR', '10:00:00', '11:15:00', 'FO 2.702'),
    (11, 7, '002', 'MW', '14:30:00', '15:45:00', 'ECSN 2.112'),
    (12, 7, '001', 'TR', '08:30:00', '09:45:00', 'GR 2.530'),
    (13, 7, '501', 'TR', '17:30:00', '19:15:00', 'SCI 3.250'),
    (15, 7, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 7, '001', 'MWF', '11:00:00', '11:50:00', 'JO 3.601'),
    (17, 7, '001', 'MWF', '09:00:00', '09:50:00', 'FN 2.214'),
    (18, 7, '001', 'MW', '11:30:00', '12:45:00', 'AD 3.214'),
    (18, 7, '002', 'TR', '11:30:00', '12:45:00', 'AD 3.214'),
    (19, 7, '001', 'TR', '10:00:00', '11:15:00', 'SLC 1.102'),
    (20, 7, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 7, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 7, '001', 'TR', '11:30:00', '12:45:00', 'JSOM 2.103'),
    (21, 7, '501', 'M', '19:00:00', '21:45:00', 'JSOM 12.202'),
    (22, 7, '001', 'MW', '13:00:00', '14:15:00', 'JSOM 2.803'),
    (22, 7, '501', 'W', '19:00:00', '21:45:00', 'JSOM 2.902'),
    (23, 7, '001', 'M', '13:00:00', '15:45:00', 'JSOM 2.902'),
    (23, 7, '002', 'M', '10:00:00', '12:45:00', 'JSOM 2.902'),
    (24, 7, '001', 'MW', '10:00:00', '11:15:00', 'JSOM 2.802'),
    (24, 7, '002', 'TR', '10:00:00', '11:15:00', 'JSOM 11.210'),
    (25, 7, '0H1', 'W', '10:00:00', '11:15:00', 'ATC 2.602'),
    (25, 7, '0H2', 'W', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 7, '0H3', 'T', '11:30:00', '12:45:00', 'ATC 2.602'),
    (26, 7, '001', 'TR', '14:30:00', '15:45:00', 'ATC 2.101'),
    (26, 7, '002', 'TR', '16:00:00', '17:15:00', 'ATC 2.101'),
    (27, 7, '001', 'MW', '12:00:00', '12:50:00', 'JO 3.516'),
    (27, 7, '201', 'F', '09:00:00', '09:50:00', 'JO 4.502'),
    (27, 7, '202', 'F', '11:00:00', '11:50:00', 'JO 4.502'),
    (27, 7, '203', 'F', '12:00:00', '12:50:00', 'JO 4.502'),
    (27, 7, '204', 'F', '14:00:00', '14:50:00', 'JO 4.502'),
    (28, 7, '001', 'MW', '13:00:00', '14:15:00', 'JO 4.708'),
    -- Spring 2025 (semester 8)
    (6, 8, '001', 'TR', '16:00:00', '17:15:00', 'ECSS 2.203'),
    (6, 8, '002', 'TR', '11:30:00', '12:45:00', 'SCI 2.210'),
    (7, 8, '001', 'TR', '10:00:00', '11:15:00', 'FN 2.204'),
    (7, 8, '002', 'TR', '11:30:00', '12:45:00', 'CB 1.202'),
    (8, 8, '002', 'MW', '14:30:00', '15:45:00', 'ECSW 3.250'),
    (8, 8, '003', 'MW', '16:00:00', '17:15:00', 'ECSW 3.250'),
    (9, 8, '0H1', 'M', '14:30:00', '15:45:00', 'FO 2.404'),
    (9, 8, '0W1', NULL, NULL, NULL, 'Online'),
    (10, 8, '001', 'TR', '13:00:00', '14:15:00', 'ECSS 2.203'),
    (11, 8, '001', 'MW', '14:30:00', '15:45:00', 'FO 2.702'),
    (12, 8, '001', 'TR', '08:30:00', '09:45:00', 'FO 2.702'),
    (12, 8, '002', 'MW', '11:30:00', '12:45:00', 'FO 2.404'),
    (13, 8, '001', 'MWF', '15:00:00', '16:45:00', 'FN 2.106'),
    (14, 8, '001', 'MW', '13:00:00', '14:15:00', 'SLC 2.304'),
    (14, 8, '301', 'T', '13:00:00', '15:45:00', 'SCI 2.220'),
    (14, 8, '302', 'W', '14:30:00', '17:15:00', 'SCI 2.220'),
    (14, 8, '303', 'R', '13:00:00', '15:45:00', 'SCI 2.220'),
    (15, 8, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 8, '001', 'MWF', '12:00:00', '12:50:00', 'GR 4.301'),
    (17, 8, '001', 'MWF', '09:00:00', '09:50:00', 'GR 4.301'),
    (18, 8, '002', 'MW', '11:30:00', '12:45:00', 'SLC 2.203'),
    (18, 8, '003', 'TR', '16:00:00', '17:15:00', 'SLC 2.203'),
    (19, 8, '001', 'TR', '11:30:00', '12:45:00', 'SCI 1.210'),
    (19, 8, '002', 'TR', '08:30:00', '09:45:00', 'SCI 1.210'),
    (20, 8, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 8, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 8, '501', 'M', '19:00:00', '21:45:00', 'JSOM 12.206'),
    (22, 8, '501', 'W', '19:00:00', '21:45:00', 'JSOM 2.112'),
    (23, 8, '001', 'M', '13:00:00', '15:45:00', 'JSOM 11.206'),
    (23, 8, '002', 'M', '16:00:00', '18:45:00', 'JSOM 2.802'),
    (24, 8, '001', 'TR', '10:00:00', '11:15:00', 'JSOM 12.218'),
    (24, 8, '002', 'R', '13:00:00', '15:45:00', 'FN 2.106'),
    (25, 8, '0H1', 'T', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 8, '0H2', 'W', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 8, '0W1', NULL, NULL, NULL, 'Online'),
    (26, 8, '001', 'MW', '11:30:00', '12:45:00', 'ATC 2.918'),
    (27, 8, '001', 'TR', '11:30:00', '12:20:00', 'HH 2.402'),
    (27, 8, '201', 'F', '10:00:00', '10:50:00', 'ATC 2.101'),
    (27, 8, '202', 'F', '11:00:00', '11:50:00', 'ATC 2.101'),
    (27, 8, '203', 'F', '12:00:00', '12:50:00', 'ATC 2.101'),
    (27, 8, '204', 'F', '13:00:00', '13:50:00', 'ATC 2.602'),
    (28, 8, '501', 'T', '19:00:00', '21:45:00', 'JO 4.708'),
    -- Summer 2025 (semester 9)
    (6, 9, '0U1', 'TR', '12:30:00', '14:45:00', 'SCI 2.225'),
    (8, 9, '0U1', 'TR', '12:30:00', '14:45:00', 'JSOM 2.107'),
    (10, 9, '0W1', NULL, NULL, NULL, 'Online'),
    (13, 9, '0U1', 'MWF', '14:00:00', '16:00:00', 'SCI 3.220'),
    (19, 9, '0U1', 'MW', '10:00:00', '12:00:00', 'JSOM 2.106'),
    (21, 9, '5U1', 'T', '18:00:00', '21:45:00', 'JSOM 12.202'),
    (22, 9, '5U1', 'M', '18:00:00', '21:45:00', 'JSOM 12.202'),
    (24, 9, '0U1', 'W', '13:00:00', '16:45:00', 'JSOM 2.106'),
    (25, 9, '551', 'MW', '18:00:00', '22:30:00', 'ATC 2.918'),
    (26, 9, '0W1', NULL, NULL, NULL, 'Online'),
    -- Fall 2025 (semester 10)
    (6, 10, '001', 'TR', '16:00:00', '17:15:00', 'FN 2.214'),
    (6, 10, '501', 'TR', '17:30:00', '18:45:00', 'FN 2.214'),
    (7, 10, '001', 'TR', '10:00:00', '11:15:00', 'SCI 3.260'),
    (7, 10, '002', 'TR', '11:30:00', '12:45:00', 'SCI 3.260'),
    (8, 10, '001', 'TR', '11:30:00', '12:45:00', 'ECSN 2.112'),
    (8, 10, '002', 'MW', '08:30:00', '09:45:00', 'ECSN 2.112'),
    (8, 10, '003', 'TR', '16:00:00', '17:15:00', 'GR 3.302'),
    (9, 10, '001', 'TR', '11:30:00', '12:45:00', 'ECSW 3.250'),
    (9, 10, '0H1', 'MW', '13:00:00', '14:15:00', 'JO 3.601'),
    (10, 10, '001', 'MW', '14:30:00', '15:45:00', 'ECSW 1.365'),
    (10, 10, '501', 'MW', '17:30:00', '18:45:00', 'ECSW 1.365'),
    (11, 10, '001', 'TR', '10:00:00', '11:15:00', 'FO 2.702'),
    (11, 10, '002', 'MW', '14:30:00', '15:45:00', 'FO 2.702'),
    (12, 10, '001', 'TR', '08:30:00', '09:45:00', 'JO 4.102'),
    (13, 10, '501', 'TR', '17:30:00', '19:15:00', 'SCI 3.240'),
    (15, 10, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 10, '001', 'MWF', '11:00:00', '11:50:00', 'HH 2.502'),
    (17, 10, '001', 'MWF', '10:00:00', '10:50:00', 'HH 2.502'),
    (18, 10, '001', 'MW', '10:00:00', '11:15:00', 'CB 1.202'),
    (18, 10, '002', 'TR', '10:00:00', '11:15:00', 'SLC 2.203'),
    (18, 10, '003', 'TR', '14:30:00', '15:45:00', 'SLC 2.203'),
    (19, 10, '001', 'TR', '10:00:00', '11:15:00', 'SLC 1.102'),
    (19, 10, '002', 'TR', '08:30:00', '09:45:00', 'GR 2.530'),
    (20, 10, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 10, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 10, '001', 'TR', '11:30:00', '12:45:00', 'JSOM 2.103'),
    (21, 10, '501', 'M', '19:00:00', '21:45:00', 'JSOM 12.202'),
    (22, 10, '001', 'MW', '13:00:00', '14:15:00', 'JSOM 1.117'),
    (22, 10, '501', 'W', '19:00:00', '21:45:00', 'SOM 1.102'),
    (23, 10, '001', 'M', '13:00:00', '15:45:00', 'JSOM 2.902'),
    (23, 10, '002', 'W', '16:00:00', '18:45:00', 'JSOM 2.901'),
    (24, 10, '001', 'MW', '10:00:00', '11:15:00', 'JSOM 2.802'),
    (24, 10, '002', 'TR', '10:00:00', '11:15:00', 'JSOM 2.801'),
    (25, 10, '0H1', 'W', '10:00:00', '11:15:00', 'ATC 2.602'),
    (25, 10, '0H2', 'W', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 10, '0H3', 'T', '11:30:00', '12:45:00', 'ATC 2.602'),
    (26, 10, '001', 'TR', '14:30:00', '15:45:00', 'ATC 2.602'),
    (26, 10, '002', 'TR', '16:00:00', '17:15:00', 'ATC 2.101'),
    (28, 10, '001', 'MW', '13:00:00', '14:15:00', 'JO 4.708'),
    -- Spring 2026 (semester 11)
    (6, 11, '001', 'TR', '13:00:00', '14:15:00', 'SCI 2.210'),
    (6, 11, '002', 'TR', '11:30:00', '12:45:00', 'JO 4.614'),
    (7, 11, '001', 'TR', '10:00:00', '11:15:00', 'SCI 3.240'),
    (7, 11, '002', 'TR', '11:30:00', '12:45:00', 'CB 1.206'),
    (8, 11, '001', 'TR', '11:30:00', '12:45:00', 'ECSN 2.112'),
    (8, 11, '002', 'MW', '14:30:00', '15:45:00', 'ECSW 3.250'),
    (8, 11, '003', 'MW', '16:00:00', '17:15:00', 'ECSW 3.250'),
    (9, 11, '001', 'MW', '08:30:00', '09:45:00', 'FO 1.202'),
    (9, 11, '0W1', NULL, NULL, NULL, 'Online'),
    (10, 11, '001', 'TR', '13:00:00', '14:15:00', 'ECSW 1.355'),
    (11, 11, '001', 'MW', '14:30:00', '15:45:00', 'FO 2.702'),
    (12, 11, '001', 'TR', '08:30:00', '09:45:00', 'FO 2.702'),
    (12, 11, '002', 'MW', '11:30:00', '12:45:00', 'FN 2.214'),
    (13, 11, '001', 'MWF', '16:00:00', '17:15:00', 'SCI 3.230'),
    (14, 11, '001', 'MW', '13:00:00', '14:15:00', 'SCI 3.230'),
    (14, 11, '301', 'T', '13:00:00', '15:45:00', 'SCI 2.220'),
    (14, 11, '302', 'W', '14:30:00', '17:15:00', 'SCI 2.220'),
    (14, 11, '303', 'R', '13:00:00', '15:45:00', 'SCI 2.220'),
    (15, 11, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 11, '001', 'MWF', '12:00:00', '12:50:00', 'GR 4.301'),
    (17, 11, '001', 'MWF', '12:00:00', '12:50:00', 'AD 3.216'),
    (18, 11, '002', 'MW', '11:30:00', '12:45:00', 'SLC 2.304'),
    (18, 11, '003', 'TR', '16:00:00', '17:15:00', 'SLC 2.203'),
    (19, 11, '001', 'TR', '08:30:00', '09:45:00', 'GR 4.428'),
    (19, 11, '002', 'TR', '11:30:00', '12:45:00', 'SCI 1.210'),
    (20, 11, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 11, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 11, '001', 'MW', '08:30:00', '09:45:00', NULL),
    (21, 11, '501', 'M', '19:00:00', '21:45:00', NULL),
    (22, 11, '001', 'MW', '08:30:00', '09:45:00', NULL),
    (22, 11, '501', 'W', '19:00:00', '21:45:00', NULL),
    (23, 11, '001', 'M', '13:00:00', '15:45:00', 'JSOM 2.801'),
    (23, 11, '002', 'M', '16:00:00', '18:45:00', 'JSOM 2.802'),
    (24, 11, '001', 'TR', '10:00:00', '11:15:00', 'JSOM 12.218'),
    (24, 11, '002', 'F', '13:00:00', '15:45:00', 'JSOM 2.801'),
    (24, 11, '003', 'MW', '11:30:00', '12:45:00', 'JSOM 11.206'),
    (25, 11, '0H1', 'T', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 11, '0H2', 'W', '11:30:00', '12:45:00', 'ATC 2.101'),
    (25, 11, '0W1', NULL, NULL, NULL, 'Online'),
    (26, 11, '001', 'MW', '11:30:00', '12:45:00', 'ATC 2.918'),
    (28, 11, '001', 'TR', '13:00:00', '14:15:00', 'FN 2.304'),
    (28, 11, '501', 'T', '19:00:00', '21:45:00', 'JO 4.708'),
    -- Summer 2026 (semester 12)
    (6, 12, '0U1', 'TR', '12:30:00', '14:45:00', 'SCI 2.225'),
    (8, 12, '0U1', 'TR', '12:30:00', '14:45:00', 'JSOM 2.107'),
    (9, 12, '0W1', 'TR', '13:00:00', '17:00:00', 'Online'),
    (10, 12, '0W1', NULL, NULL, NULL, 'Online'),
    (13, 12, '0U1', 'MWF', '14:00:00', '16:00:00', 'SCI 3.220'),
    (19, 12, '0U1', 'MW', '10:00:00', '12:00:00', 'PHY 1.202'),
    (19, 12, '0W1', NULL, NULL, NULL, 'Online'),
    (21, 12, '5U1', 'T', '18:00:00', '21:45:00', 'JSOM 2.106'),
    (22, 12, '5U1', 'T', '18:00:00', '21:45:00', 'JSOM 11.202'),
    (24, 12, '0U1', 'W', '13:00:00', '16:45:00', 'JSOM 2.106'),
    (26, 12, '0W1', NULL, NULL, NULL, 'Online'),
    -- Fall 2026 (semester 13)
    (6, 13, '001', 'TR', '16:00:00', '17:15:00', 'FN 2.214'),
    (6, 13, '501', 'TR', '17:30:00', '18:45:00', 'FN 2.214'),
    (7, 13, '001', 'TR', '10:00:00', '11:15:00', 'SCI 3.260'),
    (7, 13, '002', 'TR', '11:30:00', '12:45:00', 'SCI 3.260'),
    (8, 13, '001', 'MW', '10:00:00', '11:15:00', 'ECSN 2.112'),
    (8, 13, '002', 'MW', '08:30:00', '09:45:00', 'ECSN 2.112'),
    (8, 13, '003', 'TR', '16:00:00', '17:15:00', 'SCI 2.225'),
    (8, 13, '004', 'MW', '13:00:00', '14:15:00', 'ECSN 2.120'),
    (9, 13, '001', 'MW', '11:30:00', '12:45:00', 'ECSW 3.250'),
    (9, 13, '0W1', 'MW', '13:00:00', '14:15:00', 'Online'),
    (10, 13, '001', 'MW', '14:30:00', '15:45:00', 'ECSW 1.365'),
    (10, 13, '002', 'TR', '13:00:00', '14:15:00', 'ECSW 3.210'),
    (10, 13, '501', 'MW', '17:30:00', '18:45:00', 'ECSW 1.365'),
    (11, 13, '001', 'TR', '10:00:00', '11:15:00', 'SLC 2.304'),
    (11, 13, '002', 'MW', '14:30:00', '15:45:00', 'GR 4.301'),
    (12, 13, '001', 'TR', '08:30:00', '09:45:00', NULL),
    (13, 13, '501', 'TR', '17:30:00', '19:15:00', 'SCI 3.260'),
    (15, 13, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 13, '001', 'MWF', '11:00:00', '11:50:00', 'JO 3.601'),
    (17, 13, '001', 'MWF', '10:00:00', '10:50:00', 'SLC 2.304'),
    (18, 13, '002', 'TR', '10:00:00', '11:15:00', 'SLC 3.102'),
    (18, 13, '003', 'TR', '13:30:00', '14:45:00', 'SLC 1.204'),
    (18, 13, '004', 'MW', '14:30:00', '15:45:00', 'SLC 3.102'),
    (19, 13, '001', 'TR', '10:00:00', '11:15:00', 'SLC 1.102'),
    (19, 13, '002', 'TR', '11:30:00', '12:45:00', 'JO 3.516'),
    (20, 13, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 13, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 13, '001', 'TR', '11:30:00', '12:45:00', NULL),
    (21, 13, '002', 'TR', '13:00:00', '14:15:00', NULL),
    (21, 13, '501', 'M', '19:00:00', '21:45:00', NULL),
    (22, 13, '001', 'MW', '16:00:00', '17:15:00', NULL),
    (22, 13, '501', 'W', '19:00:00', '21:45:00', NULL),
    (23, 13, '001', 'M', '13:00:00', '15:45:00', NULL),
    (23, 13, '002', 'M', '10:00:00', '12:45:00', NULL),
    (24, 13, '001', 'MW', '10:00:00', '11:15:00', NULL),
    (24, 13, '002', 'TR', '10:00:00', '11:15:00', NULL),
    (25, 13, '0H1', 'W', '10:00:00', '11:15:00', 'ATC 2.602'),
    (25, 13, '0H2', 'W', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 13, '0H3', 'T', '11:30:00', '12:45:00', 'ATC 2.602'),
    (27, 13, '001', 'MW', '13:00:00', '13:50:00', 'FN 2.102'),
    (27, 13, '201', 'R', '09:00:00', '09:50:00', 'FN 2.104'),
    (27, 13, '202', 'F', '09:00:00', '09:50:00', 'FN 2.104'),
    (27, 13, '203', 'F', '10:00:00', '10:50:00', 'FN 2.104'),
    (27, 13, '204', 'F', '11:00:00', '11:50:00', 'FN 2.104'),
    (27, 13, '205', 'F', '12:00:00', '12:50:00', 'FN 2.104'),
    (27, 13, '206', 'F', '13:00:00', '13:50:00', 'ATC 2.918'),
    (28, 13, '501', 'MW', '17:30:00', '18:45:00', 'FN 2.304'),
    -- Spring 2027 (semester 14)
    (6, 14, '001', 'TR', '13:00:00', '14:15:00', 'HH 2.502'),
    (6, 14, '002', 'TR', '11:30:00', '12:45:00', 'HH 2.502'),
    (7, 14, '001', 'TR', '10:00:00', '11:15:00', 'SCI 3.240'),
    (7, 14, '002', 'TR', '11:30:00', '12:45:00', 'SLC 1.204'),
    (8, 14, '001', 'TR', '11:30:00', '12:45:00', 'ECSN 2.112'),
    (8, 14, '002', 'MW', '13:00:00', '14:15:00', 'JO 4.102'),
    (8, 14, '003', 'MW', '16:00:00', '17:15:00', 'GR 2.530'),
    (9, 14, '001', 'MW', '14:30:00', '15:45:00', 'FO 2.702'),
    (9, 14, '0W1', NULL, NULL, NULL, 'Online'),
    (10, 14, '001', 'TR', '10:00:00', '11:15:00', 'ECSW 1.355'),
    (11, 14, '001', 'MW', '14:30:00', '15:45:00', 'CB 1.219'),
    (12, 14, '001', 'TR', '08:30:00', '09:45:00', 'AD 2.232'),
    (12, 14, '0W1', 'TR', '11:30:00', '12:45:00', 'Online'),
    (13, 14, '001', 'MWF', '16:00:00', '17:15:00', 'SCI 3.230'),
    (14, 14, '001', 'MW', '13:00:00', '14:15:00', 'SCI 3.230'),
    (14, 14, '301', 'T', '13:00:00', '15:45:00', 'SCI 2.220'),
    (14, 14, '302', 'W', '14:30:00', '17:15:00', 'SCI 2.220'),
    (14, 14, '303', 'R', '13:00:00', '15:45:00', 'SCI 2.220'),
    (15, 14, '101', 'MF', '14:30:00', '17:15:00', 'SCI 1.188'),
    (16, 14, '001', 'MWF', '12:00:00', '12:50:00', 'FN 2.214'),
    (17, 14, '001', 'MWF', '10:00:00', '10:50:00', 'FO 3.616'),
    (18, 14, '001', 'MW', '08:30:00', '09:45:00', 'SLC 3.102'),
    (18, 14, '002', 'MW', '13:00:00', '14:15:00', 'SLC 3.102'),
    (18, 14, '003', 'TR', '13:00:00', '14:15:00', 'SLC 3.102'),
    (19, 14, '001', 'TR', '08:30:00', '09:45:00', 'SLC 1.102'),
    (19, 14, '002', 'TR', '11:30:00', '12:45:00', 'SCI 1.210'),
    (20, 14, '001', 'TR', '13:00:00', '14:15:00', 'SLC 1.214'),
    (20, 14, '002', 'TR', '14:30:00', '15:45:00', 'SLC 1.214'),
    (21, 14, '001', 'MW', '14:30:00', '15:45:00', NULL),
    (21, 14, '501', 'M', '19:00:00', '21:45:00', NULL),
    (22, 14, '001', 'MW', '10:00:00', '11:15:00', NULL),
    (22, 14, '501', 'T', '19:00:00', '21:45:00', NULL),
    (23, 14, '001', 'M', '13:00:00', '15:45:00', NULL),
    (23, 14, '002', 'M', '16:00:00', '18:45:00', NULL),
    (23, 14, '003', 'M', '13:00:00', '15:45:00', NULL),
    (24, 14, '001', 'TR', '10:00:00', '11:15:00', NULL),
    (24, 14, '002', 'F', '13:00:00', '15:45:00', NULL),
    (24, 14, '003', 'MW', '11:30:00', '12:45:00', NULL),
    (25, 14, '0H1', 'T', '11:30:00', '12:45:00', 'ATC 2.602'),
    (25, 14, '0H2', 'T', '13:00:00', '14:15:00', 'ATC 2.602'),
    (25, 14, '0W1', NULL, NULL, NULL, 'Online'),
    (28, 14, '001', 'TR', '10:00:00', '11:15:00', 'CB 1.218');

-- Who taught each section in the first list of class sections, from the UTD Nebula API. One line per professor.
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

-- Who taught each section in the second list of class sections, from the UTD Nebula API. One line per
-- professor.
INSERT INTO teaching_history (professor_id, section_id) VALUES
    (1, 351), (1, 420), (1, 421), -- Adele Amberly
    (2, 594), (2, 665), -- Ansel Ashcombe
    (4, 338), (4, 515), (4, 516), -- Cassius Birchfield
    (5, 539), (5, 540), (5, 612), -- Delphine Blackmere
    (7, 340), (7, 416), (7, 417), (7, 487), (7, 509), (7, 595), -- Fiona Brightwater
    (9, 349), (9, 350), (9, 418), (9, 423), (9, 511), (9, 521), -- Hollis Carrowmore
    (11, 357), (11, 532), (11, 533), (11, 537), (11, 538), (11, 669), -- Jude Cindervale
    (12, 345), (12, 405), (12, 409), (12, 512), -- Kaia Clearbrook
    (14, 664), -- Marisol Dalgrave
    (16, 404), (16, 410), (16, 485), (16, 589), (16, 590), -- Odette Dunmore
    (19, 335), (19, 337), (19, 412), (19, 413), (19, 484), (19, 581), (19, 588), (19, 663), -- Silas Elderidge
    (20, 431), (20, 609), -- Thea Emberton
    (21, 493), (21, 611), -- Ulric Fairhaven
    (22, 339), (22, 424), (22, 425), (22, 601), (22, 602), -- Vivian Fernsby
    (26, 520), (26, 522), (26, 605), -- Arlo Glimmerton
    (27, 608), -- Bianca Goldmere
    (29, 348), (29, 519), -- Dahlia Hallowell
    (31, 342), (31, 343), (31, 352), (31, 353), (31, 427), (31, 428), (31, 486), (31, 488), (31, 491), (31, 513), (31, 514), (31, 523), (31, 524), (31, 596), (31, 603), (31, 604), (31, 666), -- Avery Hazelwood
    (33, 411), (33, 414), (33, 504), (33, 583), (33, 591), (33, 592), -- Camille Highmoor
    (44, 347), (44, 419), (44, 422), (44, 489), (44, 490), (44, 518), (44, 525), (44, 597), (44, 667), -- Nikhil Meadowcroft
    (47, 358), (47, 529), (47, 531), (47, 534), (47, 536), (47, 610), -- Quinn Moorfield
    (48, 508), -- Rosa Northcott
    (49, 355), (49, 403), (49, 429), (49, 507), (49, 527), (49, 528), (49, 582), (49, 584), (49, 606), (49, 607), -- Soren Oakhurst
    (54, 426), (54, 599), (54, 600), -- Xiomara Ridgeway
    (56, 344), (56, 346), -- Zara Rosedale
    (57, 336), (57, 341), (57, 407), (57, 408), (57, 415), (57, 503), (57, 510), (57, 586), (57, 587), (57, 593), -- Anika Saltmarsh
    (63, 355), (63, 406), (63, 429), (63, 502), (63, 527), (63, 528), (63, 585), (63, 606), (63, 607), -- Gemma Underhill
    (66, 517), (66, 598), (66, 668), -- Jasper Willowby
    (68, 359), (68, 430), (68, 492), (68, 530), (68, 535), -- Tariq Alderwick
    (69, 356), (69, 530), -- Ivo Applethorne
    (70, 354), (70, 526), -- Daphne Ashenford
    (71, 506), -- Lena Barrowcliff
    (72, 505), -- Ezra Beechcombe
    (73, 930), (73, 1020), (73, 1021), -- Joaquin Bellmoor
    (74, 362), (74, 363), (74, 434), (74, 435), (74, 543), (74, 544), (74, 615), (74, 616), (74, 683), (74, 684), (74, 731), (74, 732), (74, 790), (74, 791), (74, 829), (74, 830), (74, 890), (74, 891), (74, 931), (74, 932), (74, 983), (74, 984), (74, 1022), (74, 1023), (74, 1075), (74, 1076), (74, 1122), (74, 1123), -- Abel Blythewood
    (75, 613), (75, 1120), (75, 1121), -- Ravi Bramblecote
    (76, 494), -- Matteo Briarwood
    (77, 361), (77, 432), (77, 542), (77, 614), (77, 730), (77, 828), (77, 889), -- Bryony Brindlecombe
    (78, 360), (78, 433), (78, 541), (78, 682), (78, 729), (78, 788), (78, 789), (78, 827), (78, 888), (78, 929), (78, 981), (78, 982), (78, 1073), (78, 1074), -- Vesna Burnhollow
    (79, 670), (79, 777), (79, 879), (79, 971), (79, 1062), -- Benedikt Calderwood
    (80, 681), -- Kasimir Cedarholt
    (81, 733), (81, 831), -- Linnea Cloverdale
    (82, 436), (82, 617), (82, 792), (82, 892), (82, 985), (82, 987), (82, 1024), (82, 1079), (82, 1124), -- Aurelio Copperhall
    (83, 438), (83, 619), (83, 735), (83, 833), (83, 880), (83, 934), (83, 1025), (83, 1026), (83, 1125), (83, 1126), -- Amara Cragmoor
    (84, 437), (84, 618), (84, 685), (84, 734), (84, 832), (84, 933), (84, 1077), (84, 1080), -- Cecily Crestwater
    (85, 686), (85, 793), (85, 893), (85, 986), (85, 1078), -- Ingrid Daisybank
    (86, 364), (86, 365), (86, 495), (86, 546), (86, 671), (86, 778), (86, 972), (86, 1063), -- Valentin Dappleton
    (87, 496), (87, 672), -- Kenji Dovecote
    (88, 545), -- Tobias Driftmere
    (89, 673), (89, 779), (89, 834), (89, 1027), (89, 1064), (89, 1127), -- Bettina Elmsworth
    (90, 366), (90, 367), (90, 439), (90, 440), (90, 441), (90, 442), (90, 443), (90, 548), (90, 549), (90, 550), (90, 621), (90, 622), (90, 688), (90, 736), (90, 835), (90, 895), (90, 935), (90, 936), (90, 989), (90, 1028), (90, 1082), (90, 1128), -- Giulia Embervale
    (91, 368), (91, 369), (91, 444), (91, 445), (91, 551), (91, 552), (91, 689), (91, 690), (91, 796), (91, 797), (91, 896), (91, 897), (91, 973), (91, 990), (91, 991), (91, 1065), (91, 1083), (91, 1084), (91, 1085), -- Mabel Evenwood
    (92, 623), (92, 674), (92, 738), (92, 780), (92, 836), (92, 881), (92, 937), (92, 1029), (92, 1129), -- Rufus Fallowmere
    (93, 737), (93, 795), -- Otis Fernhollow
    (94, 547), (94, 687), (94, 794), (94, 894), (94, 988), (94, 1081), -- Gaspard Fieldgate
    (95, 620), -- Mireille Flintwick
    (96, 371), (96, 446), (96, 554), (96, 624), (96, 692), (96, 739), (96, 799), (96, 837), -- Caspian Galeborne
    (97, 801), (97, 837), (97, 899), (97, 938), (97, 993), (97, 1030), (97, 1087), (97, 1130), -- Lorcan Gildenmoor
    (98, 370), (98, 553), (98, 691), (98, 798), (98, 898), -- Henrik Glassbrook
    (99, 372), (99, 447), (99, 448), (99, 555), (99, 693), (99, 800), (99, 900), (99, 939), (99, 994), (99, 1031), (99, 1088), (99, 1131), -- Hana Gorsefield
    (100, 625), (100, 740), (100, 838), -- Adaeze Hartmoor
    (101, 626), (101, 741), (101, 839), (101, 940), (101, 1032), (101, 1132), -- Katya Hawkridge
    (102, 992), (102, 1086), -- Corin Heronsgate
    (103, 743), (103, 744), (103, 745), (103, 746), (103, 942), (103, 943), (103, 944), (103, 945), (103, 1034), (103, 1035), (103, 1036), (103, 1037), -- Zoltan Hollyfield
    (104, 374), (104, 375), (104, 376), (104, 377), (104, 557), (104, 695), (104, 803), (104, 845), (104, 902), (104, 946), (104, 996), (104, 1038), (104, 1090), (104, 1138), -- Lior Holmewood
    (105, 454), (105, 455), (105, 456), (105, 632), (105, 747), -- Dmitri Ivybridge
    (106, 373), (106, 449), (106, 497), (106, 556), (106, 627), (106, 675), (106, 694), (106, 742), (106, 781), (106, 802), (106, 840), (106, 882), (106, 901), (106, 941), (106, 974), (106, 995), (106, 1033), (106, 1066), (106, 1089), (106, 1133), -- Noor Jadecombe
    (107, 450), (107, 451), (107, 452), (107, 453), -- Emrys Kettleby
    (108, 628), (108, 629), (108, 630), (108, 631), -- Anders Kingsmere
    (109, 841), (109, 842), (109, 843), (109, 844), -- Ilse Lanternfield
    (110, 1134), (110, 1135), (110, 1136), (110, 1137), -- Leopold Lavenham
    (111, 906), (111, 1093), -- Sabine Leafwood
    (112, 381), (112, 461), (112, 561), (112, 637), (112, 676), (112, 698), (112, 751), (112, 752), (112, 782), (112, 807), (112, 849), (112, 850), (112, 905), (112, 949), (112, 950), (112, 1001), (112, 1042), (112, 1095), (112, 1143), -- Aiko Linnetford
    (113, 804), (113, 904), (113, 947), (113, 997), (113, 1039), (113, 1091), (113, 1139), -- Oskar Loamsby
    (114, 378), (114, 379), (114, 559), (114, 634), (114, 697), (114, 748), (114, 805), (114, 847), (114, 948), (114, 998), (114, 1040), (114, 1092), (114, 1140), -- Eamon Maplethorpe
    (115, 382), (115, 459), (115, 562), (115, 636), (115, 700), (115, 701), (115, 750), (115, 806), (115, 808), (115, 848), (115, 999), (115, 1000), (115, 1041), (115, 1094), (115, 1141), -- Alma Mistlecombe
    (116, 457), (116, 558), (116, 633), (116, 696), (116, 846), (116, 903), -- Saoirse Mossgrove
    (117, 380), (117, 383), (117, 458), (117, 460), (117, 560), (117, 563), (117, 635), (117, 699), -- Quentin Nettlefold
    (118, 749), -- Gwen Oakenshaw
    (119, 1142), -- Freya Orchardleigh
    (120, 638), (120, 753), (120, 783), (120, 883), (120, 975), -- Desmond Otterburn
    (121, 952), -- Yvette Pebbleton
    (122, 564), (122, 702), (122, 809), (122, 907), (122, 1002), (122, 1096), (122, 1145), -- Malik Pinegrove
    (123, 1003), (123, 1043), (123, 1067), (123, 1068), (123, 1144), -- Ulla Quillfeather
    (124, 385), (124, 386), (124, 462), (124, 463), (124, 565), (124, 566), (124, 639), (124, 640), (124, 703), (124, 704), (124, 754), (124, 755), (124, 810), (124, 811), (124, 852), (124, 853), (124, 908), (124, 909), (124, 953), (124, 954), (124, 1004), (124, 1005), (124, 1045), (124, 1046), (124, 1098), (124, 1099), (124, 1146), (124, 1147), -- Nadia Quinceworth
    (125, 1067), -- Jalen Reedmoor
    (126, 851), (126, 951), -- Xavier Ripplewood
    (127, 1044), (127, 1097), (127, 1145), -- Niamh Rushcombe
    (128, 384), (128, 564), (128, 702), (128, 809), (128, 907), (128, 1002), (128, 1096), -- Calla Sagebrook
    (129, 390), (129, 391), (129, 392), (129, 393), (129, 467), (129, 468), (129, 469), (129, 470), (129, 569), (129, 570), (129, 643), (129, 644), (129, 707), (129, 708), (129, 757), (129, 758), (129, 814), (129, 815), (129, 855), (129, 856), (129, 912), (129, 913), -- Selim Sandmere
    (130, 387), (130, 388), (130, 567), (130, 568), (130, 756), (130, 812), (130, 910), (130, 1006), (130, 1100), (130, 1101), -- Rosalind Shadowfen
    (131, 499), (131, 678), (131, 785), (131, 885), (131, 956), (131, 1070), (131, 1150), (131, 1151), -- Priya Skylark
    (132, 389), (132, 464), (132, 465), (132, 466), (132, 498), (132, 641), (132, 642), (132, 677), (132, 705), (132, 784), (132, 884), -- Faye Sloeberry
    (133, 706), (133, 813), (133, 854), (133, 911), (133, 955), (133, 976), (133, 1007), (133, 1047), (133, 1069), (133, 1102), (133, 1148), (133, 1149), -- Paloma Sparrowhawk
    (134, 977), (134, 1008), (134, 1009), (134, 1049), (134, 1050), (134, 1103), -- Idris Swallowdale
    (135, 1104), -- Florian Tamberlyn
    (136, 1048), -- Clio Thornfield
    (137, 396), (137, 397), (137, 473), (137, 500), (137, 573), (137, 574), (137, 648), (137, 711), (137, 712), (137, 762), (137, 818), (137, 819), (137, 917), -- Frida Tidewell
    (138, 1010), (138, 1011), (138, 1105), (138, 1154), -- Elif Timberlake
    (139, 647), (139, 761), (139, 859), (139, 860), (139, 960), (139, 978), -- Halvard Umberleigh
    (140, 471), (140, 472), (140, 645), (140, 646), (140, 759), (140, 760), (140, 857), (140, 858), (140, 957), (140, 958), (140, 1051), (140, 1052), (140, 1152), (140, 1153), -- Ramona Vervain
    (141, 394), (141, 395), (141, 571), (141, 572), (141, 709), (141, 710), (141, 816), (141, 817), (141, 914), (141, 915), -- Chidi Wainscott
    (142, 916), (142, 959), -- August Wheatcombe
    (143, 1012), (143, 1013), (143, 1053), (143, 1055), (143, 1071), (143, 1107), (143, 1108), (143, 1155), (143, 1157), -- Wilhelmina Whitlow
    (144, 1106), -- Hector Yarrowby
    (145, 1054), (145, 1156), -- Meera Yewbank
    (146, 652), (146, 653), (146, 680), (146, 716), (146, 717), (146, 718), (146, 719), (146, 766), (146, 767), (146, 768), (146, 769), (146, 787), (146, 823), (146, 824), (146, 825), (146, 864), (146, 865), (146, 866), (146, 867), (146, 887), (146, 921), (146, 922), (146, 964), (146, 980), (146, 1017), (146, 1018), (146, 1059), (146, 1072), -- Delia Aspenridge
    (147, 820), (147, 863), (147, 918), (147, 919), (147, 962), (147, 963), (147, 1016), (147, 1056), (147, 1058), -- Cormac Chalkridge
    (148, 501), (148, 679), (148, 786), -- Iona Copperdale
    (149, 1111), (149, 1160), -- Elodie Elmhollow
    (150, 821), (150, 822), (150, 861), (150, 862), (150, 886), (150, 920), (150, 961), (150, 979), (150, 1014), (150, 1015), (150, 1057), (150, 1109), (150, 1110), (150, 1158), (150, 1159), -- Imogen Fenwright
    (151, 398), (151, 399), (151, 400), (151, 474), (151, 475), (151, 575), (151, 576), (151, 577), (151, 649), (151, 650), (151, 651), (151, 713), (151, 714), (151, 715), (151, 763), (151, 764), (151, 765), -- Cosima Foxglove
    (152, 401), (152, 476), (152, 578), (152, 579), -- Nils Hazelgrove
    (153, 482), (153, 775), (153, 1161), -- Marguerite Inglemoor
    (154, 661), (154, 877), (154, 965), (154, 966), (154, 967), (154, 968), (154, 969), (154, 1060), -- Edda Mallowbrook
    (155, 477), (155, 478), (155, 479), (155, 480), (155, 481), (155, 654), (155, 655), (155, 656), (155, 657), (155, 658), (155, 659), (155, 660), (155, 720), (155, 721), (155, 722), (155, 723), (155, 724), (155, 725), (155, 726), (155, 770), (155, 771), (155, 772), (155, 773), (155, 774), (155, 868), (155, 869), (155, 870), (155, 871), (155, 872), (155, 873), (155, 874), (155, 875), (155, 876), (155, 923), (155, 924), (155, 925), (155, 926), (155, 927), (155, 1112), (155, 1113), (155, 1114), (155, 1115), (155, 1116), (155, 1117), (155, 1118), -- Junia Netherby
    (156, 483), -- Tilde Pennywhistle
    (157, 662), (157, 776), (157, 878), (157, 970), (157, 1061), -- Petra Rowanberry
    (158, 402), (158, 580), (158, 727), (158, 826), (158, 928), (158, 1019), (158, 1119), -- Greer Thistledown
    (159, 483), (159, 728); -- Basil Willowmere

-- =====================================================================================================================
-- Made-up demo data
-- =====================================================================================================================

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

-- A Mathematics professor outside the committee's school, so the tests can check that committee access stops
-- at the school boundary.
INSERT INTO professor (department_id, professor_rank, first_name, last_name, email, hire_date, active_status)
SELECT department_id, 'ASSOCIATE', 'Isla', 'Wrenfield', 'isla.wrenfield@example.edu', DATE '2025-08-25', TRUE
FROM department WHERE department_code = 'MATH';

-- A hire date for every active professor, and a rank where the Nebula title names none (NULL keeps Nebula's).
CREATE LOCAL TEMPORARY TABLE demo_faculty (email VARCHAR(255) PRIMARY KEY, professor_rank VARCHAR(20), hire_date DATE);
INSERT INTO demo_faculty VALUES
    -- Computer Science
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
    ('kofi.winterbourne@example.edu',  'ASSISTANT', '2026-08-24'),
    -- Mathematics
    ('joaquin.bellmoor@example.edu',   NULL,        '2025-01-21'),
    ('abel.blythewood@example.edu',    NULL,        '2005-08-20'),
    ('ravi.bramblecote@example.edu',   NULL,        '2022-01-18'),
    ('vesna.burnhollow@example.edu',   NULL,        '2004-08-21'),
    ('benedikt.calderwood@example.edu', NULL,        '2022-05-09'),
    -- Electrical and Computer Engineering
    ('aurelio.copperhall@example.edu', NULL,        '2021-01-19'),
    ('amara.cragmoor@example.edu',     NULL,        '2021-01-19'),
    ('cecily.crestwater@example.edu',  NULL,        '2021-01-19'),
    ('ingrid.daisybank@example.edu',   NULL,        '2022-08-22'),
    ('valentin.dappleton@example.edu', NULL,        '2018-08-18'),
    -- Mechanical Engineering
    ('bettina.elmsworth@example.edu',  NULL,        '2022-05-23'),
    ('giulia.embervale@example.edu',   'ASSOCIATE', '2014-08-22'),
    ('mabel.evenwood@example.edu',     NULL,        '2006-08-21'),
    ('rufus.fallowmere@example.edu',   'ASSISTANT', '2022-01-18'),
    ('gaspard.fieldgate@example.edu',  'ASSOCIATE', '2021-08-23'),
    -- Bioengineering
    ('lorcan.gildenmoor@example.edu',  NULL,        '2023-08-21'),
    ('hana.gorsefield@example.edu',    NULL,        '2005-08-19'),
    ('katya.hawkridge@example.edu',    'ASSISTANT', '2022-01-18'),
    ('corin.heronsgate@example.edu',   'ASSOCIATE', '2025-08-25'),
    -- Physics
    ('zoltan.hollyfield@example.edu',  NULL,        '2023-01-17'),
    ('lior.holmewood@example.edu',     NULL,        '2012-08-22'),
    ('noor.jadecombe@example.edu',     'FULL',      '2004-08-20'),
    ('leopold.lavenham@example.edu',   'ASSISTANT', '2027-01-19'),
    -- Chemistry
    ('sabine.leafwood@example.edu',    NULL,        '2024-08-12'),
    ('aiko.linnetford@example.edu',    NULL,        '2018-08-19'),
    ('oskar.loamsby@example.edu',      NULL,        '2023-08-21'),
    ('eamon.maplethorpe@example.edu',  NULL,        '2018-08-22'),
    ('alma.mistlecombe@example.edu',   NULL,        '2008-08-22'),
    ('freya.orchardleigh@example.edu', 'ASSISTANT', '2027-01-19'),
    -- Biological Sciences
    ('malik.pinegrove@example.edu',    NULL,        '2021-08-23'),
    ('ulla.quillfeather@example.edu',  NULL,        '2025-08-25'),
    ('nadia.quinceworth@example.edu',  NULL,        '2015-08-22'),
    ('jalen.reedmoor@example.edu',     NULL,        '2026-05-11'),
    ('niamh.rushcombe@example.edu',    NULL,        '2026-01-20'),
    ('calla.sagebrook@example.edu',    'FULL',      '2008-08-18'),
    -- Accounting
    ('rosalind.shadowfen@example.edu', 'ASSOCIATE', '2018-08-16'),
    ('priya.skylark@example.edu',      'ASSOCIATE', '2021-05-24'),
    ('paloma.sparrowhawk@example.edu', 'ASSISTANT', '2022-08-22'),
    ('idris.swallowdale@example.edu',  'ASSISTANT', '2025-06-02'),
    ('florian.tamberlyn@example.edu',  'ASSISTANT', '2026-08-24'),
    ('clio.thornfield@example.edu',    'ASSISTANT', '2026-01-20'),
    -- Finance
    ('elif.timberlake@example.edu',    'ASSOCIATE', '2025-08-25'),
    ('ramona.vervain@example.edu',     'ASSISTANT', '2021-01-19'),
    ('wilhelmina.whitlow@example.edu', 'ASSOCIATE', '2025-08-25'),
    ('hector.yarrowby@example.edu',    'ASSISTANT', '2026-08-24'),
    ('meera.yewbank@example.edu',      'ASSISTANT', '2026-01-20'),
    -- Arts, Technology, and Emerging Communication
    ('delia.aspenridge@example.edu',   NULL,        '2022-01-18'),
    ('cormac.chalkridge@example.edu',  NULL,        '2023-08-21'),
    ('elodie.elmhollow@example.edu',   NULL,        '2026-08-24'),
    ('imogen.fenwright@example.edu',   'ASSISTANT', '2023-08-21'),
    -- Historical Studies
    ('marguerite.inglemoor@example.edu', NULL,        '2021-01-19'),
    ('edda.mallowbrook@example.edu',   NULL,        '2022-01-18'),
    ('junia.netherby@example.edu',     NULL,        '2021-01-19'),
    ('petra.rowanberry@example.edu',   NULL,        '2022-01-18'),
    ('greer.thistledown@example.edu',  'FULL',      '2006-08-21');
UPDATE professor p SET
    professor_rank = COALESCE(p.professor_rank, (SELECT d.professor_rank FROM demo_faculty d WHERE d.email = p.email)),
    hire_date = (SELECT d.hire_date FROM demo_faculty d WHERE d.email = p.email)
WHERE p.email IN (SELECT email FROM demo_faculty);
DROP TABLE demo_faculty;

-- Kaia is on the Assessment Committee. Passwords: Committee123! for Kaia and Faculty123! for all other professors.
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
-- Keira observes Leander in Fall 2026 (see below), so Keira can fill in and submit the observation form, and Leander
-- can then read the submitted form. They come after the first three so those keep user ids 1 to 3.
INSERT INTO user_account (professor_id, username, email, password_hash, is_active)
SELECT professor_id, 'klarkspur', email, '{bcrypt}$2a$10$RseEb1wE5zGshVN6rxKn3.vIn6X9E8zFPbcQHpQgqusGsctp2rydW', TRUE
FROM professor WHERE email = 'keira.larkspur@example.edu';
INSERT INTO user_account (professor_id, username, email, password_hash, is_active)
SELECT professor_id, 'lcoldwell', email, '{bcrypt}$2a$10$RseEb1wE5zGshVN6rxKn3.vIn6X9E8zFPbcQHpQgqusGsctp2rydW', TRUE
FROM professor WHERE email = 'leander.coldwell@example.edu';

-- Give every remaining professor a demo login while preserving the original accounts and user ids.
-- Usernames follow the same first-initial + last-name format as the accounts above.
INSERT INTO user_account (professor_id, username, email, password_hash, is_active)
SELECT p.professor_id, LOWER(SUBSTRING(p.first_name, 1, 1) || p.last_name), p.email,
    '{bcrypt}$2a$10$RseEb1wE5zGshVN6rxKn3.vIn6X9E8zFPbcQHpQgqusGsctp2rydW', TRUE
FROM professor p
WHERE NOT EXISTS (SELECT 1 FROM user_account u WHERE u.professor_id = p.professor_id)
ORDER BY p.professor_id;

INSERT INTO observation_template (created_by, template_name, version, created_date, active_status)
SELECT user_id, 'Standard Classroom Observation', 1, DATE '2024-08-01', TRUE
FROM user_account WHERE email = 'kaia.clearbrook@example.edu';

-- The form's questions, in the order the form shows them. Ratings run from 1 to 5.
INSERT INTO observation_criterion (template_id, criterion_text, question_type, required, display_order, rating_min,
    rating_max, notes_enabled)
SELECT t.template_id, q.criterion_text, q.question_type, q.required, q.display_order, q.rating_min, q.rating_max,
    q.criterion_text IN ('Student Engagement', 'Preparedness & Organization', 'Pace & Time for Questions')
FROM observation_template t CROSS JOIN (VALUES
    (1, 'Student Engagement',          'RATING', TRUE,  1,    5),
    (2, 'Preparedness & Organization', 'RATING', TRUE,  1,    5),
    (3, 'Content Presentation',        'RATING', TRUE,  1,    5),
    (4, 'Rigor & Depth',               'RATING', TRUE,  1,    5),
    (5, 'Pace & Time for Questions',   'RATING', TRUE,  1,    5),
    (6, 'Strengths observed',          'TEXT',   TRUE,  NULL, NULL),
    (7, 'Suggestions for improvement', 'TEXT',   FALSE, NULL, NULL))
    AS q(display_order, criterion_text, question_type, required, rating_min, rating_max)
WHERE t.template_name = 'Standard Classroom Observation' AND t.version = 1
ORDER BY q.display_order;

-- Two more criteria the form editor offers, left off the initial observation form.
INSERT INTO observation_criterion (template_id, criterion_text, question_type, required, display_order, rating_min,
    rating_max, selected, notes_enabled)
SELECT t.template_id, c.title, 'RATING', TRUE, c.sort_order, 1, 5, FALSE, FALSE
FROM observation_template t CROSS JOIN (VALUES
    ('Content Preparation',    8),
    ('Clarity and Audibility', 9)) AS c(title, sort_order)
WHERE t.active_status = TRUE;

-- Completed evaluations from earlier semesters, each in a section the professor taught that semester. The other
-- departments' evaluations are at the end of the file.
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

-- A seventh signed-up professor for testing Keira's replacement recommendation workflow.
-- TR 13:00 does not overlap Keira's CS 3345.HON (TR 10:00-11:15).
INSERT INTO professor(department_id,professor_rank,first_name,last_name,email,hire_date,active_status)
SELECT department_id,'FULL','Rowan','Cedarvale','rowan.cedarvale@example.edu',DATE '2026-02-01',TRUE
FROM department WHERE department_code='CS';

INSERT INTO course_section(course_id,semester_id,section_number,meeting_days,start_time,end_time,location)
SELECT co.course_id,s.semester_id,'901','TR',TIME '13:00:00',TIME '14:15:00','Demo classroom'
FROM course co CROSS JOIN semester s
WHERE co.course_code='CS' AND co.course_number='3345' AND s.term='Fall' AND s.academic_year=2026;

INSERT INTO teaching_history(professor_id,section_id)
SELECT p.professor_id,cs.section_id FROM professor p CROSS JOIN course_section cs
JOIN course co ON co.course_id=cs.course_id JOIN semester s ON s.semester_id=cs.semester_id
WHERE p.email='rowan.cedarvale@example.edu' AND co.course_code='CS' AND co.course_number='3345'
    AND cs.section_number='901' AND s.term='Fall' AND s.academic_year=2026;

INSERT INTO observee(professor_id,cycle_id,status)
SELECT p.professor_id,c.cycle_id,'ACTIVE' FROM professor p CROSS JOIN evaluation_cycle c
JOIN semester s ON s.semester_id=c.semester_id
WHERE p.email='rowan.cedarvale@example.edu' AND s.term='Fall' AND s.academic_year=2026;

INSERT INTO observer(professor_id,cycle_id,active_status)
SELECT professor_id,cycle_id,TRUE FROM observee
WHERE professor_id=(SELECT professor_id FROM professor WHERE email='rowan.cedarvale@example.edu');

INSERT INTO evaluation_signup(observee_id,section_id,signup_date,status)
SELECT oe.observee_id,t.section_id,DATE '2026-10-12','PENDING'
FROM observee oe JOIN professor p ON p.professor_id=oe.professor_id
JOIN teaching_history t ON t.professor_id=p.professor_id
WHERE p.email='rowan.cedarvale@example.edu';

INSERT INTO observer_list_state(signup_id,window_start,window_end)
SELECT es.signup_id,DATEADD('DAY',1,c.signup_deadline),c.observation_deadline
FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
JOIN evaluation_cycle c ON c.cycle_id=oe.cycle_id
WHERE oe.professor_id=(SELECT professor_id FROM professor WHERE email='rowan.cedarvale@example.edu');

INSERT INTO user_account(professor_id,username,email,password_hash,is_active)
SELECT professor_id,'rcedarvale',email,'{bcrypt}$2a$10$RseEb1wE5zGshVN6rxKn3.vIn6X9E8zFPbcQHpQgqusGsctp2rydW',TRUE
FROM professor WHERE email='rowan.cedarvale@example.edu';

-- A pre-deadline signup with no eligible observer, so AC deferral can be tested without reopening registration.
-- Bianca teaches only the graduate course level; the other seeded observers teach undergraduate levels.
INSERT INTO observee(professor_id,cycle_id,status)
SELECT p.professor_id,c.cycle_id,'ACTIVE' FROM professor p CROSS JOIN evaluation_cycle c
JOIN semester s ON s.semester_id=c.semester_id
WHERE p.email='bianca.goldmere@example.edu' AND s.term='Fall' AND s.academic_year=2026;

INSERT INTO observer(professor_id,cycle_id,active_status)
SELECT oe.professor_id,oe.cycle_id,TRUE FROM observee oe JOIN professor p ON p.professor_id=oe.professor_id
JOIN evaluation_cycle c ON c.cycle_id=oe.cycle_id JOIN semester s ON s.semester_id=c.semester_id
WHERE p.email='bianca.goldmere@example.edu' AND s.term='Fall' AND s.academic_year=2026;

INSERT INTO evaluation_signup(observee_id,section_id,signup_date,status)
SELECT oe.observee_id,cs.section_id,DATE '2026-10-12','PENDING'
FROM observee oe JOIN professor p ON p.professor_id=oe.professor_id
JOIN evaluation_cycle c ON c.cycle_id=oe.cycle_id
JOIN course_section cs ON cs.semester_id=c.semester_id JOIN course co ON co.course_id=cs.course_id
JOIN semester s ON s.semester_id=c.semester_id
WHERE p.email='bianca.goldmere@example.edu' AND co.course_code='CS' AND co.course_number='6363'
    AND cs.section_number='003' AND s.term='Fall' AND s.academic_year=2026;

INSERT INTO observer_list_state(signup_id,window_start,window_end)
SELECT es.signup_id,DATEADD('DAY',1,c.signup_deadline),c.observation_deadline
FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
JOIN professor p ON p.professor_id=oe.professor_id JOIN evaluation_cycle c ON c.cycle_id=oe.cycle_id
JOIN semester s ON s.semester_id=c.semester_id
WHERE p.email='bianca.goldmere@example.edu' AND s.term='Fall' AND s.academic_year=2026;

-- Completed evaluations for professors in the departments added from Nebula, made up like the ones above and in
-- semesters that already have a closed cycle. They come last so the ids of every row above stay the same. With the
-- ranks and hire dates above, 13 active professors are due in Fall 2026, 15 are overdue and 73 are upcoming.
CREATE LOCAL TEMPORARY TABLE demo_history (
    email VARCHAR(255) PRIMARY KEY, term VARCHAR(10), academic_year INT, completed_on DATE, observer_email VARCHAR(255));
INSERT INTO demo_history VALUES
    -- Mathematics
    ('joaquin.bellmoor@example.edu',   'Spring', 2026, '2026-02-17', 'abel.blythewood@example.edu'),
    ('abel.blythewood@example.edu',    'Fall',   2025, '2025-09-30', 'vesna.burnhollow@example.edu'),
    ('vesna.burnhollow@example.edu',   'Spring', 2025, '2025-02-25', 'abel.blythewood@example.edu'),
    -- Electrical and Computer Engineering
    ('aurelio.copperhall@example.edu', 'Spring', 2026, '2026-02-24', 'amara.cragmoor@example.edu'),
    ('amara.cragmoor@example.edu',     'Spring', 2026, '2026-03-03', 'aurelio.copperhall@example.edu'),
    ('cecily.crestwater@example.edu',  'Spring', 2025, '2025-03-04', 'amara.cragmoor@example.edu'),
    ('ingrid.daisybank@example.edu',   'Fall',   2025, '2025-10-07', 'aurelio.copperhall@example.edu'),
    -- Mechanical Engineering
    ('bettina.elmsworth@example.edu',  'Spring', 2026, '2026-03-24', 'rufus.fallowmere@example.edu'),
    ('giulia.embervale@example.edu',   'Spring', 2025, '2025-03-25', 'rufus.fallowmere@example.edu'),
    ('mabel.evenwood@example.edu',     'Fall',   2025, '2025-10-14', 'gaspard.fieldgate@example.edu'),
    ('rufus.fallowmere@example.edu',   'Spring', 2026, '2026-03-31', 'giulia.embervale@example.edu'),
    ('gaspard.fieldgate@example.edu',  'Fall',   2025, '2025-10-21', 'giulia.embervale@example.edu'),
    -- Bioengineering
    ('lorcan.gildenmoor@example.edu',  'Spring', 2026, '2026-04-07', 'hana.gorsefield@example.edu'),
    ('hana.gorsefield@example.edu',    'Spring', 2026, '2026-02-17', 'lorcan.gildenmoor@example.edu'),
    ('katya.hawkridge@example.edu',    'Spring', 2026, '2026-02-24', 'hana.gorsefield@example.edu'),
    -- Physics
    ('zoltan.hollyfield@example.edu',  'Spring', 2026, '2026-03-03', 'lior.holmewood@example.edu'),
    ('lior.holmewood@example.edu',     'Spring', 2026, '2026-03-24', 'noor.jadecombe@example.edu'),
    ('noor.jadecombe@example.edu',     'Fall',   2025, '2025-10-28', 'lior.holmewood@example.edu'),
    -- Chemistry
    ('aiko.linnetford@example.edu',    'Fall',   2025, '2025-11-04', 'eamon.maplethorpe@example.edu'),
    ('oskar.loamsby@example.edu',      'Spring', 2026, '2026-03-31', 'eamon.maplethorpe@example.edu'),
    ('eamon.maplethorpe@example.edu',  'Spring', 2026, '2026-04-07', 'alma.mistlecombe@example.edu'),
    ('alma.mistlecombe@example.edu',   'Fall',   2025, '2025-09-30', 'aiko.linnetford@example.edu'),
    -- Biological Sciences
    ('ulla.quillfeather@example.edu',  'Spring', 2026, '2026-02-17', 'nadia.quinceworth@example.edu'),
    ('nadia.quinceworth@example.edu',  'Fall',   2025, '2025-10-07', 'calla.sagebrook@example.edu'),
    ('calla.sagebrook@example.edu',    'Fall',   2025, '2025-10-14', 'malik.pinegrove@example.edu'),
    -- Accounting
    ('rosalind.shadowfen@example.edu', 'Fall',   2025, '2025-10-21', 'idris.swallowdale@example.edu'),
    ('priya.skylark@example.edu',      'Spring', 2025, '2025-04-01', 'paloma.sparrowhawk@example.edu'),
    ('paloma.sparrowhawk@example.edu', 'Spring', 2026, '2026-02-24', 'clio.thornfield@example.edu'),
    ('idris.swallowdale@example.edu',  'Spring', 2026, '2026-03-03', 'clio.thornfield@example.edu'),
    -- Finance
    ('ramona.vervain@example.edu',     'Spring', 2026, '2026-03-24', 'meera.yewbank@example.edu'),
    -- Arts, Technology, and Emerging Communication
    ('delia.aspenridge@example.edu',   'Spring', 2026, '2026-03-31', 'imogen.fenwright@example.edu'),
    ('cormac.chalkridge@example.edu',  'Spring', 2025, '2025-04-08', 'imogen.fenwright@example.edu'),
    ('imogen.fenwright@example.edu',   'Spring', 2026, '2026-04-07', 'cormac.chalkridge@example.edu'),
    -- Historical Studies
    ('edda.mallowbrook@example.edu',   'Spring', 2025, '2025-04-15', 'petra.rowanberry@example.edu'),
    ('petra.rowanberry@example.edu',   'Spring', 2026, '2026-02-17', 'edda.mallowbrook@example.edu'),
    ('greer.thistledown@example.edu',  'Fall',   2025, '2025-10-28', 'basil.willowmere@example.edu');

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

-- Completed demo observations need answers, or a committee member who opens one sees "No response" everywhere.
INSERT INTO observation_response (observation_id, criterion_id, rating, comments)
SELECT o.observation_id, c.criterion_id,
    CASE WHEN c.question_type = 'RATING' THEN c.rating_max - MOD(o.observation_id + c.display_order * 2, 3) END,
    CASE
        WHEN c.question_type = 'RATING' THEN
            CASE WHEN c.notes_enabled AND MOD(o.observation_id + c.display_order, 3) = 0 THEN
                CASE c.display_order
                    WHEN 1 THEN 'Most students took part in the pair discussion.'
                    WHEN 2 THEN 'Slides and handouts were ready before class began.'
                    ELSE 'Questions were taken after each section of the lecture.'
                END
            END
        WHEN c.required THEN
            CASE MOD(o.observation_id, 4)
                WHEN 0 THEN 'Clear explanations built around worked examples, and students asked good follow-up questions.'
                WHEN 1 THEN 'A well-organized lecture that tied the new material back to the previous week.'
                WHEN 2 THEN 'Steady pacing with frequent checks for understanding.'
                ELSE 'Live demonstrations kept students involved for the whole class.'
            END
        ELSE
            CASE MOD(o.observation_id, 3)
                WHEN 1 THEN 'Leave a few more minutes at the end for questions.'
                WHEN 2 THEN 'Post the slides before class so students can follow along.'
            END
    END
FROM observation o
JOIN observation_criterion c ON c.template_id = o.template_id AND c.selected = TRUE
WHERE o.status = 'COMPLETED';

-- Shared email templates; delivery is simulated.
INSERT INTO message_template (template_type,name,subject,body) VALUES
('EVALUATION_DUE','Evaluation due','Faculty Evaluation Due - {{cycleLabel}}','Dear Professor {{lastName}},

You are due for a faculty evaluation during the {{cycleLabel}} evaluation cycle. Please sign in to the Assessment Helper and complete your evaluation signup by the stated deadline.

If you have already completed the required signup, no further action is needed at this time.

Thank you,
Assessment Committee'),
('EVALUATION_DUE_FOLLOW_UP','Evaluation due follow-up','Follow Up: Faculty Evaluation Due - {{cycleLabel}}','Dear Professor {{lastName}},

This is a follow-up reminder. You are due for a faculty evaluation during the {{cycleLabel}} evaluation cycle. Please sign in to the Assessment Helper and complete your evaluation signup by the stated deadline.

If you have already completed the required signup, no further action is needed at this time.

Thank you,
Assessment Committee'),
('EVALUATION_OVERDUE','Evaluation overdue','Faculty Evaluation Overdue - {{cycleLabel}} Evaluation Required','Dear Professor {{lastName}},

You are overdue for a faculty evaluation. {{evaluationHistory}} As {{rankDescription}}, you are required to participate in an evaluation once every {{interval}}. Please complete the {{cycleLabel}} evaluation cycle for your outstanding evaluation. Please sign in to the Assessment Helper and complete your evaluation sign-up by the stated deadline.

If you have already completed the required sign-up, no further action is needed at this time.

Thank you,
Assessment Committee'),
('EVALUATION_OVERDUE_FOLLOW_UP','Evaluation overdue follow-up','Follow Up: Faculty Evaluation Overdue - {{cycleLabel}} Evaluation Required','Dear Professor {{lastName}},

This is a follow-up reminder about your overdue faculty evaluation. {{evaluationHistory}} As {{rankDescription}}, you are required to participate in an evaluation once every {{interval}}. Please complete the {{cycleLabel}} evaluation cycle for your outstanding evaluation. Please sign in to the Assessment Helper and complete your evaluation sign-up by the stated deadline.

If you have already completed the required sign-up, no further action is needed at this time.

Thank you,
Assessment Committee');
