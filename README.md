# Expert-System

Assignment 1 — Artificial Intelligence & Machine Learning (II2, ENSI).

A Java Swing desktop app with two interactive modules, both built on a “whiteboard”: the problem is drawn as a graph, then the algorithm is replayed step by step.

- **Expert Systems**: forward and backward chaining over facts and rules.
- **Search Algorithms**: BFS, DFS, Uniform Cost, Greedy Best-First and A* on a weighted directed graph.

## Features

### Expert Systems

- Facts, rules and the goal are drawn as nodes and arrows on a whiteboard, with no rule syntax to type.
- Forward chaining and backward chaining.
- Step-by-step replay with Start, Prev, Next, Play / Pause and Reset.
- Import and export of the board as a `.txt` file.
- Cycle detection in backward chaining.

### Search Algorithms

- Weighted directed graph with editable nodes and arrow weights.
- Start node, goal node and a heuristic value h(n) per node (used by Greedy and A*).
- Breadth-first (BFS), Depth-first (DFS), Uniform cost (Dijkstra), Greedy best-first and A*.
- Step-by-step replay showing the frontier, expanded nodes, final path, total cost and number of expanded nodes.

## Board file format (Expert Systems)

Plain text, one statement per line, `#` for comments:

```text
# comments start with #
FACTS: has fever, has cough, tired
GOAL: needs rest
IF has fever AND has cough THEN flu
IF flu AND tired THEN needs rest
has cough, tired -> cold
cold -> needs rest
```

## Project structure

```text
src/
├── MainGUI.java            Main menu and entry point
├── ExpertSystemPanel.java  Expert system screen
├── Whiteboard.java         Whiteboard for facts, rules and goal
├── ExpertSystem.java       Knowledge base (facts + rules)
├── Rule.java               IF ... THEN ... rule
├── ForwardChaining.java    Forward chaining algorithm
├── BackwardChaining.java   Backward chaining algorithm
├── SearchPanel.java        Search screen
├── SearchBoard.java        Whiteboard for the weighted graph
├── SearchGraph.java        Graph model (edges, heuristics)
├── SearchAlgorithms.java   BFS, DFS, UCS, Greedy, A*
└── GraphPanel.java         Graph drawing panel
ex.txt                      Example expert-system board
```

## Build and run

On Windows, install JDK 21 or newer, open PowerShell in the project folder, and run:

```powershell
.\build.ps1
```

The script creates:

- `dist/Expert-System/Expert-System.exe` — Windows app with a bundled Java runtime. Keep the full `dist/Expert-System` folder together when moving it.
- `dist/Expert-System.jar` — executable JAR; run with `java -jar dist/Expert-System.jar` (requires Java).

## Author

Jihed ([@mouelhijihed](https://github.com/mouelhijihed)) — 2nd-year Computer Science Engineering student, ENSI.
