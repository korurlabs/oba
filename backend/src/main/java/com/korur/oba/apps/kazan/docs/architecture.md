# Kazan

## Board

Board is the top element in the system. It can have child boards and parent board. In boards, we can manage our backlog and sprints (which includes the tickets), projects, labels and members of that board.

### Members

Boards can have members, which is the board should be accessible only for those members.

### Sprints

Boards can have multiple sprints, which is the ticket are created in and the timeline of the tickets are managed.

### Backlog

Boards can have a backlog, which includes the tickets. Backlog tickets can be moved to selected Sprint.

### Projects

Boards can have multiple projects, which provides a grouping layer for the tickets (tickets can only have 1 project linked).

### Labels

Boards can have multiple labels, which provides a tagging system for the tickets (tickets can have multiple labels).

## Tickets

Tickets are the main element of this system.

Tickets:

1. Must belong to a board.
2. Have one of the types `BUG`, `FEATURE`, `TECH_DEBT`, `IMPROVEMENT`, `INCIDENT`, `PROBLEM` or `REFACTOR`. The priority scale depends on the type: `P1` to `P3` for `INCIDENT`, `LOWEST` to `HIGHEST` for every other type. The API still accepts `TASK` from older clients as a deprecated alias and stores it as `FEATURE`.
3. Have one of the statuses `TODO`, `IN_PROGRESS`, `WAITING_FOR_THIRD_PARTY` (blocked on someone outside the team), `IN_REVIEW` or `DONE`. A top-level backlog ticket is always `TODO`.
4. Have `assignees` (who is responsible) and `reviewers` (who is going to review).
5. Can be in the sprint or backlog (not both of them).
6. Can be linked to a project which will provide ticket grouping for the board.
7. Can have sub tickets — or parent ticket.
8. Can have comments.

### Comments

Tickets can have multiple comments, which provides interaction to the tickets.

# Flow

## 1. Creating a board

Boards are the top element of the system. It has backlog and sprints which we assign/create the tickets. To create a board, we need:

* Name
* Description
* Prefix
* Color
* Icon
* Parent board (if it’s a child board)

Once the board is created, backlog for that board will be created by default.

We can assign which members should access to the board.

## 2. Creating a sprint

Sprints are the section that we usually create our tickets in provided timeline. We can create multiple sprints for a board, which can show us the deadlines for the tickets. To create a sprint, we need:

* Name
* Goal
* Start date
* End date
* Board

Every sprint will have:

* Number: which will counted by the board’s sprints (first sprint will be 1, second will be 2 etc.)
* Status: `PLANNED` (created but not started yet), `ACTIVE` (currently in progress), `COMPLETED` (finished). If there is any active sprints in the board, planned sprints can not be started.

## 3. Creating a ticket

Tickets are the main element of this system. Each ticket will be created for a specific board. Ticket can be in a backlog or a sprint. To create a ticket, we need:

* Board
* Sprint or Backlog
* Title
* Description
* Type
* Priority
* Story points
* Mission rank
* RCA
* Lessons learned

### 3.1. Creating a ticket for parent board with parent board’s sprint

This is a functionality that we are using by default. We will create a ticket for a board that has a sprint.

#### Example

**Board:** Sensey Board

**Sprint:** First Sprint (11.03.2026 - 25.03.2026) - Related to Sensey Board

This ticket will shown in the Sensey Board’s First Sprint.

### 3.2. Creating a ticket for a child board with parent board’s sprint

This is a functionality that we can use for using a common/shared sprint for each child boards. We will create a ticket for a child board but we will use the parent board’s sprint.

#### Example

**Board:** Fabrikam Board (child board for Sensey Board)

**Sprint:** First Sprint (11.03.2026 - 25.03.2026) - Related to Sensey Board (parent board)

This ticket will shown in the Sensey Board with First Sprint selected, and Fabrikam Board with First Sprint selected. We have to show all the child boards sprints if we want to show the child boards’ tickets.

**Disclaimer:** This can be complicated in both back-end and UI/UX level. 

### 3.3. Creating a ticket for parent board with child board’s sprint

This functionality should not be allowed. It’s not logical to create a ticket in the parent board with child board’s sprint. If we want to see the tickets in the parent board, we can show the child board’s tickets, so the ticket should be created for the child board and child board’s sprint.

# Architecture
Board
├── Child Boards
├── Backlog
│   └── Tickets
├── Sprints
│   └── Tickets
├── Projects
│   └── Tickets
├── Members
│   └── User
├── Labels
│   └── Tickets
└── Tickets
    ├── Sub Tickets
    ├── Assignees
    │   └── User/Assignee
    ├── Reviewers
    │   └── User/Reviewer
    ├── Comments
    │   └── User/Author
    ├── Backlog
    ├── Sprint
    └── Project

# Endpoints

## Backlogs (by board)

* Get backlog details (with tickets)
* Create ticket for backlog

PS: I don’t think we need a controller for Backlogs. Backlog controller is handling only the tickets for backlogs; this can be handled in Ticket controller in my opinion.

## Boards

* Create board
* List boards
* Get board detail
* Update board
* Children boards list
* Hierarchy of the board
* Ancestor list of the board
* Member management (this should be separated)

## Comments (by ticket)

* Simple CRUD

## Labels (by board)

* Simple CRUD

## Projects (by board)

* Simple CRUD

## Sprints (by board)

* Create sprint
* List sprints
* Get current sprint
* Get sprint detail
* Update sprint
* Start sprint
* Complete sprint
* Delete sprint

## Tickets (by board)

* Create ticket
* List tickets
* Get ticket detail
* Update ticket
* Delete ticket
* Move ticket
* Update ticket order — I don’t think this is necessary, we can use the default update
* Create sub ticket