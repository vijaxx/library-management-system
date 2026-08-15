-- Demo data, loaded only when the books table is empty.

INSERT INTO books (isbn, title, author, category, total_copies, available_copies) VALUES
('978-0134685991', 'Effective Java',                        'Joshua Bloch',        'Programming', 4, 4),
('978-0132350884', 'Clean Code',                            'Robert C. Martin',    'Programming', 3, 3),
('978-0201633610', 'Design Patterns',                       'Erich Gamma',         'Programming', 2, 2),
('978-0596009205', 'Head First Design Patterns',            'Eric Freeman',        'Programming', 3, 3),
('978-1617294945', 'Modern Java in Action',                 'Raoul-Gabriel Urma',  'Programming', 2, 2),
('978-0321356680', 'Java Concurrency in Practice',          'Brian Goetz',         'Programming', 2, 2),
('978-0262033848', 'Introduction to Algorithms',            'Thomas H. Cormen',    'Algorithms',  5, 5),
('978-0321573513', 'Algorithms',                            'Robert Sedgewick',    'Algorithms',  2, 2),
('978-0073523323', 'Database System Concepts',              'Abraham Silberschatz','Databases',   3, 3),
('978-0136006633', 'Operating System Concepts',             'Abraham Silberschatz','Systems',     3, 3),
('978-0132126953', 'Computer Networking: A Top-Down Approach','James F. Kurose',   'Networks',    2, 2),
('978-0134610993', 'Computer Organization and Design',      'David A. Patterson',  'Systems',     2, 2),
('978-0596517748', 'JavaScript: The Good Parts',            'Douglas Crockford',   'Programming', 1, 1),
('978-1449331818', 'Learning SQL',                          'Alan Beaulieu',       'Databases',   2, 2),
('978-0140449136', 'The Odyssey',                           'Homer',               'Classics',    2, 2),
('978-0143105428', 'Meditations',                           'Marcus Aurelius',     'Philosophy',  2, 2),
('978-0099518471', 'Midnight''s Children',                  'Salman Rushdie',      'Fiction',     2, 2),
('978-0143039563', 'The God of Small Things',               'Arundhati Roy',       'Fiction',     2, 2);

INSERT INTO members (name, email, phone, membership_type, join_date, active) VALUES
('Kondani Vijay Vardhan', 'vijay@example.com',   '+91-90000-00001', 'PREMIUM',  DATE '2024-01-15', TRUE),
('Ananya Sharma',         'ananya@example.com',  '+91-90000-00002', 'STUDENT',  DATE '2024-03-02', TRUE),
('Rahul Verma',           'rahul@example.com',   '+91-90000-00003', 'STANDARD', DATE '2024-05-19', TRUE),
('Meera Iyer',            'meera@example.com',   '+91-90000-00004', 'STUDENT',  DATE '2025-01-08', TRUE),
('Arjun Nair',            'arjun@example.com',   '+91-90000-00005', 'STANDARD', DATE '2025-02-21', TRUE),
('Priya Deshpande',       'priya@example.com',   '+91-90000-00006', 'PREMIUM',  DATE '2025-06-30', TRUE);
