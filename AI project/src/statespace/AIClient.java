package statespace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import statespace.Strategy.*;
import statespace.Heuristic.*;

public class AIClient {
	private static int MAX_ROW;
	private static int MAX_COL;

	private static boolean[][] walls;
	private boolean[][] tempWalls;
	private static Goal[][] goals;
	private static Agent[][] agents;
	private static Agent[] agentIDs;
	private static Box[][] boxes;
	private static int agentCounter = 0;

	private ArrayList<Goal> goalList;
	private LinkedList<ArrayList<Goal>> goalPriorityList;
	private Map<String, Box[][]> boxMap = new HashMap<>();
	private Map<String, Goal[][]> goalMap = new HashMap<>();
	private Map<Goal, String> goalColor = new HashMap<>();
	// private Map<Character, ArrayList<Goal>> goalMap;
	private static Map<Agent, Node> initialStates = new HashMap<>();

	private Map<Character, ArrayList<Goal>> goalListMap = new HashMap<>();
	private Map<Character, ArrayList<Box>> boxListMap = new HashMap<>();
	private Map<Goal, Integer[][]> dijkstraGoalMap = new HashMap<>();
	private Map<Box, Integer[][]> dijkstraBoxMap = new HashMap<>();
	

	private final static Set<String> COLORS = new HashSet<>(
			Arrays.asList("blue", "red", "green", "cyan", "magenta", "orange", "pink", "yellow"));
	
	private static final String DEFAULT_COLOR = "blue";

	// private BufferedReader in = new BufferedReader(new
	// InputStreamReader(System.in));
	// private List<Agent> agents = new ArrayList<Agent>();

	private AIClient(BufferedReader in) throws IOException {
		Map<Character, String> chrColorMap = new HashMap<>();
		Map<String, List<Character>> colorChrMap = new HashMap<>();
		Map<String, Integer> colorAgents = new HashMap<>();
		Map<String, ArrayList<Goal>> colorGoals = new HashMap<>();
		
		String line, color;

		// Read lines specifying colors
		while ((line = in.readLine()).matches("^[a-z]+:\\s*[0-9A-Z](,\\s*[0-9A-Z])*\\s*$")) {
			line = line.replaceAll("\\s", "");
			color = line.split(":")[0];
			if (!COLORS.contains(color))
				throw new ColorException("Color not defined");
			// else if (colorChrMap.containsKey(color))
			// throw new ColorException("Color defined multiple times..");

			List<Character> colorObjects = new ArrayList<>();
			if (colorChrMap.containsKey(color)) {
				colorObjects = colorChrMap.get(color);
			}
			for (String id : line.split(":")[1].split(",")) {
				colorObjects.add(id.charAt(0));
				chrColorMap.put(id.charAt(0), color);
			}
			colorChrMap.put(color, colorObjects);
		}

		// Max columns and rows
		MAX_COL = line.length();
		ArrayList<String> lines = new ArrayList<>();
		while (!line.equals("")) {
			lines.add(line);
			line = in.readLine();
			MAX_COL = line.length() > MAX_COL ? line.length() : MAX_COL;
		}
		MAX_ROW = lines.size();

		// Initialize arrays
		boxMap.put(DEFAULT_COLOR, new Box[MAX_ROW][MAX_COL]);
		goalMap.put(DEFAULT_COLOR, new Goal[MAX_ROW][MAX_COL]);
		for (String currentColor : colorChrMap.keySet()) {
			boxMap.put(currentColor, new Box[MAX_ROW][MAX_COL]);
			goalMap.put(currentColor, new Goal[MAX_ROW][MAX_COL]);
		}

		walls = new boolean[MAX_ROW][MAX_COL];
		tempWalls = new boolean[MAX_ROW][MAX_COL];
		agents = new Agent[MAX_ROW][MAX_COL];
		goals = new Goal[MAX_ROW][MAX_COL];
		boxes = new Box[MAX_ROW][MAX_COL];
		goalList = new ArrayList<Goal>();
		goalPriorityList = new LinkedList<ArrayList<Goal>>();
		// goalMap = new HashMap<Character, ArrayList<Goal>>();

		// Read lines specifying level layout
		for (int row = 0; row < lines.size(); row++) {
			line = lines.get(row);
			for (int col = 0; col < line.length(); col++) {
				char chr = line.charAt(col);

				if (chr == '+') { // Wall.
					walls[row][col] = true;
				} else if ('0' <= chr && chr <= '9') { // Agent.
					String c = chrColorMap.get(chr);
					if (c == null)
						c = DEFAULT_COLOR;

					Agent a = new Agent(chr, c, this);
					agents[row][col] = a;

					if (colorAgents.get(c) == null)
						colorAgents.put(c, 0);
					
					colorAgents.put(c, colorAgents.get(c) + 1);
					agentCounter++;
				} else if ('A' <= chr && chr <= 'Z') { // Box.
					String c = chrColorMap.get(chr);
					if (c == null)
						c = DEFAULT_COLOR;

					Box box = new Box(chr, c, new Pos(row, col));

					boxes[row][col] = box;
					boxMap.get(c)[row][col] = box;
					
					if (!boxListMap.containsKey(chr)) 
						boxListMap.put(chr, new ArrayList<Box>());
					boxListMap.get(chr).add(box);
					
				} else if ('a' <= chr && chr <= 'z') { // Goal.
					String c = chrColorMap.get(Character.toUpperCase(chr));
					if (c == null)
						c = DEFAULT_COLOR;

					Goal goal = new Goal(chr, new Pos(row, col));
					goals[row][col] = goal;
					goalMap.get(c)[row][col] = goal;
					goalColor.put(goal, c);

					goalList.add(goal);

					if (!colorGoals.containsKey(c))
						colorGoals.put(c, new ArrayList<Goal>());
					colorGoals.get(c).add(goal);

					if (!goalListMap.containsKey(chr))
						goalListMap.put(chr, new ArrayList<Goal>());
					goalListMap.get(chr).add(goal);

					// if (!goalMap.containsKey(chr))
					// goalMap.put(chr, new ArrayList<Goal>());
					// goalMap.get(chr).add(goal);
				} else if (chr == ' ') {
					// Free space.
				} else {
					System.err.println("Error, read invalid level character: " + chr);
					System.exit(1);
				}
			}
		}

		// Prioritize the goals
		ArrayList<Integer> goalListCount = new ArrayList<Integer>();
		for (int i = 0; i < goalList.size(); i++)
			goalListCount.add(0);

		for (int redo = goalList.size(); redo > 0; redo--) {

			for (int i = 0; i < redo; i++) {
				Goal goal = goalList.get(i);

				boolean[][] tempMap = new boolean[MAX_ROW][MAX_COL];

				for (int row = 0; row < MAX_ROW; row++)
					System.arraycopy(walls[row], 0, tempMap[row], 0, MAX_COL);

				for (int goalCount = 0; goalCount < redo; goalCount++) {
					Goal g = goalList.get(goalCount);
					if (g != goal) {
						tempMap[g.getPos().row][g.getPos().col] = true;
					}
				}

				int countFields = 0;
				LinkedList<Pos> queue = new LinkedList<>();
				queue.add(goal.getPos());
				ArrayList<Pos> checked = new ArrayList<>();
				while (true) {

					if (queue.isEmpty())
						break;
					Pos pos = queue.poll();

					if (0 <= pos.row - 1 && !tempMap[pos.row - 1][pos.col]) {
						Pos p = new Pos(pos.row - 1, pos.col);
						if (!checked.contains(p)) {
							queue.add(p);
							checked.add(p);
							countFields++;
						}
					}
					if (MAX_ROW > pos.row + 1 && !tempMap[pos.row + 1][pos.col]) {
						Pos p = new Pos(pos.row + 1, pos.col);
						if (!checked.contains(p)) {
							queue.add(p);
							checked.add(p);
							countFields++;
						}
					}
					if (0 <= pos.col - 1 && !tempMap[pos.row][pos.col - 1]) {
						Pos p = new Pos(pos.row, pos.col - 1);
						if (!checked.contains(p)) {
							queue.add(p);
							checked.add(p);
							countFields++;
						}
					}
					if (MAX_COL > pos.col + 1 && !tempMap[pos.row][pos.col + 1]) {
						Pos p = new Pos(pos.row, pos.col + 1);
						if (!checked.contains(p)) {
							queue.add(p);
							checked.add(p);
							countFields++;
						}
					}

					tempMap[pos.row][pos.col] = true;
				}

				goalListCount.remove(i);
				goalListCount.add(i, countFields);
			}
			
			ArrayList<Goal> tempGoals = new ArrayList<Goal>();
			int heighestReachCells = 0;
			for (int i = 0; i < redo; i++) {
				if (goalListCount.get(i) > heighestReachCells) {
					heighestReachCells = goalListCount.get(i); 
				}
			}
			
			for (int i = 0; i < redo; i++) {
				if (goalListCount.get(i) == heighestReachCells) {
					tempGoals.add(goalList.get(i)); 
				}
			}
			
			goalPriorityList.addFirst(new ArrayList<Goal>(tempGoals));
					// And now sort the prioritizing of goals
			for (int i = 0; i < redo; i++) {
				for (int j = 0; j < redo - 1; j++) {
					if (goalListCount.get(j) > goalListCount.get(j + 1)) {
						Integer tempCount = goalListCount.get(j);
						goalListCount.remove(j);
						goalListCount.add(j + 1, tempCount);

						Goal temp = goalList.get(j);
						goalList.remove(j);
						goalList.add(j + 1, temp);
					}
				}
			}
			
		}

		// Get openings
		for (int i = 0; i < goalList.size(); i++) {
			Goal g = goalList.get(i);
//			checkAlley(g);
			System.err.println(g+" gets "+i);
			g.priority = i;
		}

		for (Map.Entry<Character, ArrayList<Goal>> entry : goalListMap.entrySet()) {
			ArrayList<Goal> goals = entry.getValue();

			for (Goal goal : goals) {
				Integer[][] dijkstra = new Integer[MAX_ROW][MAX_COL];

				// Pair of currentPos, corresponding parentPos

				ArrayList<Pair<Pos,Pos>> queue = new ArrayList<>();
				queue.add(new Pair<Pos, Pos>(goal.getPos(), null));
				
				while(!queue.isEmpty()) {
					Pair<Pos, Pos> pair = queue.get(0);
					queue.remove(0);
					Pos pos = (Pos) pair.getLeft();
					Pos parentPos = (Pos) pair.getRight();
					
					if (dijkstra[pos.row][pos.col] != null || walls[pos.row][pos.col]) continue;
					if (parentPos == null) 
						dijkstra[pos.row][pos.col] = 0;
					else
						dijkstra[pos.row][pos.col] = dijkstra[parentPos.row][parentPos.col] + 1;
					
					if (0 <= pos.row - 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row - 1, pos.col), pos));
					if (MAX_ROW > pos.row + 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row + 1, pos.col), pos));
					if (0 <= pos.col- 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col - 1), pos));
					if (MAX_COL > pos.col + 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col + 1), pos));
				}
				dijkstraGoalMap.put(goal, dijkstra);
			}
		}
		
		
		for (Map.Entry<Character, ArrayList<Box>> entry : boxListMap.entrySet()) {
			ArrayList<Box> boxes = entry.getValue();
			for (Box box : boxes) {
				Integer[][] dijkstra = new Integer[MAX_ROW][MAX_COL];
				
				// Pair of currentPos, corresponding parentPos
				ArrayList<Pair<Pos,Pos>> queue = new ArrayList<>();
				queue.add(new Pair<Pos, Pos>(box.getPos(), null));
				
				while(!queue.isEmpty()) {
					Pair<Pos, Pos> pair = queue.get(0);
					queue.remove(0);
					Pos pos = (Pos) pair.getLeft();
					Pos parentPos = (Pos) pair.getRight();
					
					if (dijkstra[pos.row][pos.col] != null || walls[pos.row][pos.col]) continue;
					if (parentPos == null) 

						dijkstra[pos.row][pos.col] = 0;
					else
						dijkstra[pos.row][pos.col] = dijkstra[parentPos.row][parentPos.col] + 1;

					if (0 <= pos.row - 1)
						queue.add(new Pair<Pos, Pos>(new Pos(pos.row - 1, pos.col), pos));
					if (MAX_ROW > pos.row + 1)
						queue.add(new Pair<Pos, Pos>(new Pos(pos.row + 1, pos.col), pos));
					if (0 <= pos.col - 1)
						queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col - 1), pos));
					if (MAX_COL > pos.col + 1)
						queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col + 1), pos));
				}
				dijkstraBoxMap.put(box, dijkstra);
			}
		}
		
		//Create initial state
		combinedSolution = new ArrayList<>();

		state = new MultiNode(this, boxes, agents);
		combinedSolution.add(state);

		// Create initial states
		agentIDs = new Agent[getAgentNum()];
		
		long start = System.currentTimeMillis();
		for (int row = 0; row < MAX_ROW; row++) {
			for (int col = 0; col < MAX_COL; col++) {
				Agent a = agents[row][col];
				if (a != null) {
					agentIDs[a.getID()] = a;
					String c = a.getColor();
					Node initialState = new Node(null, this, a, new Pos(row, col), null);
					
					//Give reachable boxes
					ArrayList<Box> reachableBoxesList = new ArrayList<>();
					Box[][] reachableBoxes = new Box[MAX_ROW][MAX_COL];
					for (int row2 = 0; row2 < MAX_ROW; row2++) {
						for (int col2 = 0; col2 < MAX_COL; col2++) {
							Box b = boxes[row2][col2];
							if(b != null && a.getColor().equals(b.getColor())) {
								if (dijkstraBoxMap.get(b)[row][col] == null) continue;
								else {
									reachableBoxes[row2][col2] = b;
									reachableBoxesList.add(b);
								}
							}
						}
					}

					initialState.boxes = reachableBoxes;
					// initialState.boxes = boxMap.get(c);
					a.setReachableBoxes(reachableBoxesList);
					
					//If agents of same color, only solve for reachable goals 
					if(colorAgents.get(c) > 1) {
						Goal[][] reachableGoals = new Goal[MAX_ROW][MAX_COL];
						ArrayList<Goal> reachableGoalsList = new ArrayList<>();
						if (colorGoals.get(c) != null) { // ACCOUNT FOR AGENTS WITHOUT A GOAL
							for (Goal g : colorGoals.get(c)) {
								if (g != null) {
									if (dijkstraGoalMap.get(g)[row][col] == null) continue;		// if no dijkstra value, goal is not reachable for the agent
									else {
										reachableGoals[g.getPos().row][g.getPos().col] = g;
										reachableGoalsList.add(g);
									}
								}
							}
						}
						a.reachableGoals = reachableGoalsList;
						initialState.goals = reachableGoals;
					} else {
						initialState.goals = goalMap.get(c);
						if(colorGoals.get(c) != null) a.reachableGoals = colorGoals.get(c);

					}
					initialStates.put(a, initialState);
					// System.err.println(a+"'s reachable boxes: "+reachableBoxesList+"\n
					// "+initialState);
					
					System.err.println("Agent " + a);
					System.err.println("Agent " + a + " has goals " + a.reachableGoals.toString());
					System.err.println("Agent " + a + " has boxes " + a.reachableBoxes.toString());
				}
			}
		}
		
		System.err.println("STATES : "+ (System.currentTimeMillis()-start));
	}

	private static Strategy getStrategy(String strategyStr, Node initialState) {
		Strategy strategy;

		switch (strategyStr.toLowerCase()) {
		case "bfs":
			strategy = new StrategyBFS();
			break;
		case "dfs":
			strategy = new StrategyDFS();
			break;
		case "astar":
			strategy = new StrategyBestFirst(new AStar(initialState));
			break;
		case "wastar":
			// You're welcome to test WA* out with different values, but for the report you
			// must at least indicate benchmarks for W = 5.
			strategy = new StrategyBestFirst(new WeightedAStar(initialState, 5));
			break;
		case "greedy":
			strategy = new StrategyBestFirst(new Greedy(initialState));
			break;
		default:
			strategy = new StrategyBFS();
			System.err.println(
					"Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy.");

		}
		return strategy;
	}

	public static int getMaxRow() {
		return MAX_ROW;
	}

	public static int getMaxCol() {
		return MAX_COL;
	}

	public Goal[][] getGoals(Agent agent) {
		return goalMap.get(agent.getColor());
	}

	public ArrayList<Goal> getGoalList() {
		return goalList;
	}

	public boolean[][] getWalls() {
		return walls;
	}

	public boolean[][] getTempWalls() {
		return tempWalls;
	}

	public void resetTempWalls() {
		tempWalls = new boolean[MAX_ROW][MAX_COL];
	}

	public Box[][] getBoxes() {
		return boxes;
	}

	public Agent[][] getAgents() {
		return agents;
	}

	public Map<Goal, Integer[][]> getDijkstraMap() {
		return this.dijkstraGoalMap;
	}
	
	public Map<Character, ArrayList<Goal>> getGoalListMap() {
		return goalListMap;
	}
	
	public ArrayList<Node> search(Strategy strategy, Node initialState) throws IOException {
//		System.err.format("Search starting with strategy %s.\n", strategy.toString());
		
		if(strategy.frontierIsEmpty()) { //first time
			strategy.addToFrontier(initialState);
		}
		// System.err.println(initialState);
		// System.err.println(initialState.goToBox);
		// System.err.println(initialState.goToGoal);
		int iterations = 0;
		while (true) {
			if (iterations == 1000) {
				// System.err.println(strategy.searchStatus());
				iterations = 0;
			}

			if (strategy.frontierIsEmpty()) {
				return null;
			}

			Node leafNode = strategy.getAndRemoveLeaf();

			// Goal states
			if (leafNode.isGoalState()) {
				return leafNode.extractPlan();
			}

			strategy.addToExplored(leafNode);
			for (Node n : leafNode.getExpandedNodes()) {
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					// n.calculateDistanceToGoal();
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}

	public static AIClient client;
	private static ArrayList<MultiNode> combinedSolution;
	private static Pos[][] requests;
	private static String[] actions;
	private static Node[] currentStates, backupStates;
	private static MultiNode state;
	private static ArrayList<Agent> agentOrder;
	private static ArrayList<ArrayList<Node>>[]  allSolutions;
	private static ArrayList<Node>[] solutions;
	private static Strategy[] strategies;
	private static ArrayList<String> actionList;
	private static int reachedAgent;

	public static void main(String[] args) throws Exception {
		BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in));

		// Read level and create the initial state of the problem
		client = new AIClient(serverMessages);
		solutions = new ArrayList[agentCounter];
		allSolutions = new ArrayList[agentCounter];
		strategies = new Strategy[agentCounter];
		agentOrder = new ArrayList<>();
		actionList = new ArrayList<>();
		requests = new Pos[agentCounter][7];
		currentStates = new Node[agentCounter];
		backupStates = new Node[agentCounter];

		int deadlockLimit = 0;

		// Find solutions
		for (Agent a : initialStates.keySet()) {
			System.err.println("Find initial solution for agent " + a);

			Node initialState = initialStates.get(a);

			ArrayList<Box> aBoxes = a.getBoxesNotInGoal();
			Box box = aBoxes.isEmpty() ? null : aBoxes.get(0);
			
			initialState.goToBox = box;
			initialState.goTo = true;
			// System.err.println("goToGoal: "+initialState.goToGoal);
			// System.err.println("goToGoal: "+initialState.goToBox);
			// System.exit(0);

			// Set box to be in working process - not to be moved by another agent
			// initialState.goToBox.inWorkingProcess = true;

			Strategy strategy = getStrategy("astar", initialState);
			ArrayList<Node> solution = createSolution(strategy, client, initialState);
			strategies[a.getID()] = strategy;

			if (solution != null) {
				solutions[a.getID()] = solution;

				updateRequirements(solution, a.getID());
				
				System.err.println("Initial solution for agent " + a.getID());
				System.err.println(solution);
			} else {
				System.err.println("COULD NOT FIND SOLUTION FOR " + a);
			}
		}

		orderAgents();
		
		System.err.println("Initial order of agents: " + agentOrder);

		for (Agent a : initialStates.keySet()) {
			currentStates[a.getID()] = initialStates.get(a);
		}

		while (true) {
			boolean stop = false;
			actions = new String[agentCounter];
			boolean done = true;
			System.err.println("\n---------- NEXT STATE -------------");
			
			System.err.println("initial\n" + state);
			System.err.println("Agent order: " + agentOrder);

			reachedAgent = 0;
			while (reachedAgent < agentOrder.size()) {
				int a = agentOrder.get(reachedAgent).getID();
				System.err.println("Currently " + agentIDs[a] + " is helping " + agentIDs[a].isHelping
						+ " and gets help from " + agentIDs[a].getsHelp);

				if (actions[a] == null) { // no action yet
					System.err.println("Agent: " + a + " turn at " + reachedAgent + " in order");

					// If current agent is done with its current goal
					if ((solutions[a] == null || solutions[a].isEmpty())) {
						System.err.println("You made it to the goal!!!");
						// System.err.println(currentStates[a]);
						// if (currentStates[a].goToGoal != null)
						// System.err.println(currentStates[a].goToGoal.getPos());

						// IF current agent is getting help
						// AND current agent is helping the same agent that helps him
						// AND current agent is allowed to get help
						if (agentIDs[a].getsHelp != null && agentIDs[a].getsHelp == agentIDs[a].isHelping
								&& agentIDs[a].getHelp) {
							agentIDs[a].isHelping.getHelp = true;
							agentIDs[a].isHelping.isHelping = null;
							agentIDs[a].isHelping.getsHelp = null;
							agentIDs[a].isHelping = null;
							agentIDs[a].getsHelp = null;
						}

						Node newInitialState = currentStates[a].copy();

						if (!newInitialState.isRealGoalState()) {

							newInitialState.boxPosition = null;
							newInitialState.requestedPositions = null;
							newInitialState.agentGoal = null;
							newInitialState.ignore = true;
							newInitialState.updateBoxes();

							System.err.println("goToBox: " + newInitialState.goToBox+ " "+newInitialState.goTo);
							
							// Switch subgoal
							if (newInitialState.goToBox != null) {
								newInitialState.goTo = !newInitialState.goTo;
							}

							setUpOwnSolution(newInitialState, a);

							// Create solution with box
							if (!newInitialState.isGoalState() && newInitialState.goToBox != null) {

								// Create solution to solve own problem
								createOwnSolution(newInitialState, a);

							} else {
								newInitialState.goTo = !newInitialState.goTo;

								setUpOwnSolution(newInitialState, a);
								
								if(!newInitialState.isGoalState() && newInitialState.goToBox != null) {
									
									//Create solution to solve own problem
									createOwnSolution(newInitialState, a);
								}
							}
						}
					}

					// Execute
					// System.err.println("EXECUTE: "+a1+ " \n"+solutions[a1]+ " REQUIRING
					// "+Arrays.toString(requests[a1]));
					actions[a] = getAction(solutions, a, reachedAgent);

					// At least one agent has a proper action
					if (actions[a] != "NoOp") {
						done = false;
					}
					// Next helper

					System.err.println(Arrays.toString(actions));
					
					if((solutions[a] != null && !solutions[a].isEmpty())) {
						getHelp(reachedAgent, a, solutions[a].get(0));
					} else {
						if (agentIDs[a].waiting > 0) {
							agentIDs[a].waiting--;
							System.err.println("Decrement waiting, since the job is done");
						}
					}

//					if ((solutions[a] != null && !solutions[a].isEmpty())) {
//						if (agentIDs[a].getHelp) {
//							getHelp(reachedAgent, a, solutions[a].get(0));
//						} else {
//							System.err.println("CANNOT GET HELP");
//						}
//					}
				}
				System.err.println("THIS IS THE CURRENT STATE LALSLDASDLLASDLASLDLASLDALSDLASLDLASD");
				System.err.println(currentStates[a]);
				reachedAgent++;
			}
			if (stop) {
				break;
			}

			// OUT OF INNER LOOP
			boolean execute = true;
			if (done) {
				if (state.isGoalState()) {
					System.err.println("COMPLETED");
					break;
				} else {
					System.err.println("DEADLOCK: try reset agents - try " + deadlockLimit);

					// Replan
					// resetAgents();
					// startOver(strategies, agentOrder.get(0));
					execute = false;
					
					boolean agentWaiting = false;
					for(Agent a : agentIDs) {
						if (a.waiting > 0) {
							agentWaiting = true;
							break;
						}
					}
					
					if (!agentWaiting) {
						if (deadlockLimit < 1) {
							for(Agent a : agentIDs) {
								resetAgent(a.getID());
							}
							
							deadlockLimit++;
						} else {
							break;
						}						
					}
				}
			}

			if (execute) {
				deadlockLimit = 0;
				System.err.println("Execute actions");

				// Add state to the combined solution, since the iteration is done
				combinedSolution.add(state);

				// Completed the final goals
				// If so, release any helpers locked to this agent
				for (Agent a : agentIDs) {
					if (currentStates[a.getID()].isRealGoalState()) {

						boolean goalButHelping = false;
						for (Agent b : agentIDs) {

							if (a != b && b.getsHelp == a && a.getsHelp != b) {
								goalButHelping = true;
							}
						}

						System.err.println(currentStates[a.getID()] + "is a goal state");

						if (a.getsHelp != null && !goalButHelping && a.isHelping == null) { // && a.getsHelp !=
																							// a.isHelping) {

							System.err.println(a + " done reset");
							a.getsHelp.getHelp = true;
							a.getsHelp.isHelping = null;
							a.getsHelp = null;
							// stop = true;
						}
						System.err.println(a + " is done");
					}
				}

				// Create action string
				String act;
				act = "[";
				for (int i = 0; i < actions.length; i++) {
					act += actions[i];
					if (i < actions.length - 1) {
						act += ", ";
					}
				}
				act += "]";
				actionList.add(act);

				//////////////// TEST
				// if(act.equals("[null,null]")) {
				// System.err.println("NULL");
				// break;
				// }

				System.out.println(act);
				System.err.println(act);
				System.err.println(state);
				// for (int i = 0; i < currentStates.length; i++)
				// System.err.println(currentStates[i]);

				if (stop) {
					break;
				}
				// String response = serverMessages.readLine();
				// if (response.contains("false")) {
				// System.err.format("Server responsed with %s to the inapplicable action:
				// %s\n", response, act);
				// System.err.format("%s was attempted in \n%s\n", act, state.toString());
				// break;
				// }
			}
		}
		// OUT OF WHILE LOOP

		// System.err.println("OUTSIDE: \n"+combinedSolution);

		// for(String act : actionList) {
		// System.err.println(act);
		// System.out.println(act);
		// String response = serverMessages.readLine();
		// if (response.contains("false")) {
		// System.err.format("Server responsed with %s to the inapplicable action:
		// %s\n", response, act);
		// System.err.format("%s was attempted in \n%s\n", act, state.toString());
		// break;
		// }
		// }
		System.err.println("Final solution is of length: " + actionList.size());
		// System.err.println(actionList);
	}

	private static void setUpOwnSolution(Node newInitialState, int a) {

		if (newInitialState.goTo || newInitialState.goToBox == null) {

			if (newInitialState.goToBox != null)
				newInitialState.goToBox.inWorkingProcess = false;
			if (newInitialState.goToGoal != null)
				newInitialState.goToGoal.inWorkingProcess = false;

			System.err.println("tralalalalalalalalalalalalalallalalalalala");

			ArrayList<Box> aBoxes = agentIDs[a].getBoxesNotInGoal();
			Box box = aBoxes.isEmpty() ? null : aBoxes.get(0);

			if (box != null)
				System.err.println("Go to box " + box.pos);
			else
				System.err.println("No box to find");

			newInitialState.goToBox = box;
			newInitialState.goTo = true;
			newInitialState.goToGoal = null;

			// Set box to be in working process - not to be moved by another agent
			// newInitialState.goToBox.inWorkingProcess = true;

		} else if (!newInitialState.goTo && newInitialState.goToBox != null) {
			// Get most prioritized goal
			Goal goal = client.getPrioritizedGoal(newInitialState.goToBox, a);
//			System.err.println("Goal found " + goal + ", pos " + goal.getPos());
//			try {
//				TimeUnit.SECONDS.sleep(2);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			newInitialState.goToGoal = goal;

			// Set box to be in working process - not to be moved by another agent
			newInitialState.goToBox.inWorkingProcess = true;
			newInitialState.goToGoal.inWorkingProcess = true;

		}
	}
	
	private static boolean createOwnSolution(Node newInitialState, int a) throws IOException {
		//Create solution to solve own problem
		ArrayList<Node> solution =  createSolution(getStrategy("astar", newInitialState), client, newInitialState);
		
		if (solution != null) {
			System.err.println("New solution found");
			System.err.println("Initial State:");
			System.err.println(newInitialState);
			System.err.println("Path to goal:");
			System.err.println(solution);
	
			solutions[a] = solution;
			currentStates[a] = newInitialState;
					
			updateRequirements(solutions[a], a);
		} else {
			if (newInitialState.goToBox != null) 
				newInitialState.goToBox.inWorkingProcess = false;
			if (newInitialState.goToGoal != null ) 
				newInitialState.goToGoal.inWorkingProcess = false;
		}
		
		return solution != null;
	}

	private static void getHelp(int i, int a1, Node node) throws IOException {
		Pos p1 = requests[a1][1];
		Pos p2 = requests[a1][2];
		boolean atOne = true;

		Agent inNeed = agentIDs[a1];
		System.err.println("GET HELP FOR " + a1 + "/" + reachedAgent + " in: \n" + state + p1 + " " + p2);

		// Get helping agent
		Agent helper = null;
		Box b = null;
		String aAction = null;
		if (p1 != null) {
			helper = state.agents[p1.row][p1.col];
			b = state.boxes[p1.row][p1.col];
			if (helper == null && b == null && p2 != null) {
				helper = state.agents[p2.row][p2.col];
				b = state.boxes[p2.row][p2.col];
				atOne = false;
			}

			if (helper == null && b != null && !inNeed.reachableBoxes.contains(b)) {
				for (int j = 0; j < agentOrder.size(); j++) {
					if (agentOrder.get(j).reachableBoxes.contains(b)) {
						helper = agentOrder.get(j);
						aAction = actions[helper.getID()];
						if (aAction == null) {
							break;
						}
					}
				}
			}
		}

		// System.err.println("REQUESTS: "+Arrays.deepToString(requests));
		// System.err.println("ACTIONS: "+Arrays.toString(actions));
		// System.err.println("helper != inNeed: "+(helper != inNeed));
		System.err.println("Agent " + inNeed + " checks if he needs help");
		System.err.println("Helper: " + helper);
		System.err.println("inNeed.isHelping: " + inNeed.isHelping);
		System.err.println("inNeed.getHelp: " + inNeed.getHelp);

		if (helper != null) {
			System.err.println("helper.getsHelp: " + helper.getsHelp);
			System.err.println("helper.isHelping: " + helper.isHelping);
			System.err.println(currentStates[helper.getID()]);
		}

		// IF helper is not null
		// AND helper is not current agent (inNeed)
		// AND
		// -- helper is not helping anyone else
		// OR helper is already helping the current agent
		// OR the one the helper is helping has no actions
		// CONCLUSION, the helper can help the current agent

		if (!((!inNeed.getHelp || inNeed.waiting > 1) && inNeed.isHelping == helper) && helper != inNeed && helper != null && (helper.isHelping == null || helper.isHelping == inNeed || actions[helper.isHelping.getID()] == null || actions[helper.isHelping.getID()] == "NoOp" || actions[helper.getID()] == null || actions[helper.getID()] == "NoOp")) {

			System.err.println("Agent " + helper.getID() + " can help agent " + inNeed.getID());

			// IF helper is not getting help at all
			// AND current agent is not helping the helper
			// CONCLUSION the helper is not getting help
			if (helper.getsHelp != inNeed && (helper.getsHelp == null || actions[helper.getsHelp.getID()] == null)) {// &&
																														// inNeed.isHelping
																														// !=
																														// helper)
																														// {
				System.err.println(helper.getID() + " may help " + inNeed.getID());
				if (aAction == null) { // available helper later
					System.err.println("CASE 1");
					agentOrder.remove(helper);
					agentOrder.add(agentOrder.indexOf(inNeed) + 1, helper);
				} else if (helper.getsHelp == null) { // available helper before not getting help
					System.err.println("CASE 2");
					agentOrder.remove(helper);
					agentOrder.add(agentOrder.indexOf(inNeed) + 1, helper);
				} else if (inNeed.isHelping == null) { // available helper before getting help while current can be
														// moved
					System.err.println("CASE 3 (MAY REMOVE)");
					agentOrder.remove(inNeed);
					agentOrder.add(agentOrder.indexOf(helper), inNeed);
				}
				// updateHelpers();
				planHelp(inNeed, helper, atOne);
				reachedAgent = agentOrder.indexOf(helper) - 1;

				// If helper is already helping current agent
				// AND helper cannot get help
				// CONCLUSION they are helping each other
			} else if (helper.getsHelp == inNeed
					&& (actions[helper.getID()] == null || actions[helper.getID()] == "NoOp")) {// Mutual help &&
																								// !helper.getHelp
				helper.isHelping = inNeed;
				inNeed.getsHelp = helper;
				agentOrder.remove(helper);
				agentOrder.add(agentOrder.indexOf(inNeed) + 1, helper);
				System.err.println("CASE LOOP " + helper + " and " + inNeed);
				// System.exit(0);
				// updateHelpers();
				planHelp(inNeed, helper, atOne);
				reachedAgent = agentOrder.indexOf(helper) - 1;
			}
		}

		// The agent can soon ask for help again
		// I.e. the agent cannot ask the agent he was helping before waiting is 0
		if (inNeed.waiting > 0) {
			inNeed.waiting--;
			System.err.println("Not able to ask for help");
		}
	}

	private static boolean oneway(Pos p) {
		int count = 4;

		if (p.row - 1 > 0 && !walls[p.row - 1][p.col]) count--;
		if (p.row + 1 < getMaxRow() && !walls[p.row + 1][p.col]) count--;
		if (p.col - 1 > 0 && !walls[p.row][p.col - 1]) count--;
		if (p.col + 1 < getMaxCol() && !walls[p.row][p.col + 1]) count--;

		return count >= 2;
	}

	private static void planHelp(Agent inNeed, Agent helper, boolean atOne) throws IOException {
		int a1 = inNeed.getID();
		String plan = "";
		
		ArrayList<Node> sol = null;

		if (currentStates[helper.getID()] == null)
			System.err.println("HELPER HAS NO STATE");
		else {
			System.err.println("Helpers current state:");
			System.err.println(currentStates[helper.getID()]);
		}

		Node newInitialState = copyNode(currentStates[helper.getID()], inNeed, helper);

		if (inNeed.color == helper.color && currentStates[helper.getID()].goToGoal == null && currentStates[inNeed.getID()].goToBox != null) {

			System.err.println("Agent " + helper + " tries to take over agent " + inNeed + "'s job");

			// If another agents takes over the current agents job, reverse the current
			// agents last job, if not null or NoOp
			Node curState = null;
			String act = actions[inNeed.getID()];
			
			System.err.println("Agent " + inNeed + "'s prev action: " + actions[inNeed.getID()]);
			
			if(act != null && !act.equals("NoOp")) {
				System.err.println("Reverse agent " + inNeed + "'s action: " + act);
				
				Command reverseAction = Command.reverse(act);
				state = new MultiNode(state, inNeed.getID(), reverseAction);
				curState = currentStates[inNeed.getID()];
				currentStates[inNeed.getID()] = backupStates[inNeed.getID()];
				newInitialState.updateBoxes();
			}

			newInitialState.goToBox = currentStates[inNeed.getID()].goToBox;
			newInitialState.goToGoal = currentStates[inNeed.getID()].goToGoal;
			newInitialState.goTo = currentStates[inNeed.getID()].goTo;
			
			if (newInitialState.goToBox != null) newInitialState.goToBox.inWorkingProcess = true;
			if (newInitialState.goToGoal != null) newInitialState.goToGoal.inWorkingProcess = true;
						
			sol = createSolution(getStrategy("astar", newInitialState), client, newInitialState);

			if (newInitialState.goToBox != null)
				newInitialState.goToBox.inWorkingProcess = true;
			if (newInitialState.goToGoal != null)
				newInitialState.goToGoal.inWorkingProcess = true;

			if (sol != null) {
				currentStates[inNeed.getID()].goToBox = null;
				currentStates[inNeed.getID()].goToGoal = null;
				resetAgent(inNeed.getID());

				agentOrder.remove(inNeed);
				agentOrder.add(agentOrder.indexOf(helper) + 1, inNeed);
				
				plan = "Helping agent of same color, by taking over his plan";
			} else {

				// If no solution were found, put back the reversed action
				if(act != null && !act.equals("NoOp")) {
					System.err.println("Re-Reverse agent " + inNeed + "'s action");
					System.err.println("*************CHANGE STATE*************");
					
					state = new MultiNode(state, inNeed.getID(), curState.action);

					backupStates[inNeed.getID()] = currentStates[inNeed.getID()];
					currentStates[inNeed.getID()] = curState;
					newInitialState.updateBoxes();
				}

				if (newInitialState.goToBox != null)
					newInitialState.goToBox.inWorkingProcess = false;
				if (newInitialState.goToGoal != null)
					newInitialState.goToGoal.inWorkingProcess = false;

				newInitialState.goToBox = null;
				newInitialState.goToGoal = null;
				newInitialState.goTo = true;
			}
		}
		
		if(sol == null) {
			
			
			System.err.println("inNeed: "+inNeed+" helper:"+helper);
			ArrayList<Pos> positions = new ArrayList<>() ;
			
			if (!solutions[a1].isEmpty()) {
				positions.add(solutions[a1].get(0).getRequired());
			}
			positions.add(requests[a1][0]); //required in previous state
			positions.add(currentStates[a1].getRequired());//required in current state
			positions.add(requests[a1][1]); //p, required in next state
			positions.add(requests[a1][2]); //p2, required in after next state		
			positions.add(requests[a1][3]); //required after after next state
			positions.add(requests[a1][4]); //required after after after next state
			positions.add(requests[a1][5]); //required after after after after next state
			positions.add(requests[a1][6]); //required after after after after after next state
	
			System.err.println("Agent " + helper +" may not be in the following positions: " + positions);
			
	
			//Reverse action if getting too close
			if(atOne) {
				String act = actions[helper.getID()];
				
				if(act != null && !act.equals("NoOp")) {
					System.err.println("Reverse agent " + helper + "'s action: " + act);
					
					Command reverseAction = Command.reverse(act);
					state = new MultiNode(state, helper.getID(), reverseAction);
					currentStates[helper.getID()] = backupStates[helper.getID()];
					
					newInitialState = copyNode(currentStates[helper.getID()], inNeed, helper);
				}
				actions[helper.getID()] = null;
			}

			// Replan
			if (helper.isHelping != null) {
				helper.isHelping.getsHelp = null;
			}

			helper.isHelping = inNeed;
			inNeed.getsHelp = helper;

			// Set up some rules for this solution
			newInitialState.ignore = false;
			newInitialState.requestedPositions = positions;

			sol = createSolution(getStrategy("bfs", newInitialState), client, newInitialState);
			
			plan = "Helping agent, by moving away from requests positions";
		}
		
		if(sol == null) {
			System.err.println("Try to reverse plan if needed and replan");

			Node curState = null;
			String act = actions[inNeed.getID()];

			
			if(act != null && !act.equals("NoOp")) {
				System.err.println("Reverse agent " + inNeed + "'s action: " + act);
				System.err.println("*************CHANGE STATE*************");
				
				Command reverseAction = Command.reverse(act);
				state = new MultiNode(state, inNeed.getID(), reverseAction);
				curState = currentStates[inNeed.getID()];
				currentStates[inNeed.getID()] = backupStates[inNeed.getID()];

				actions[inNeed.getID()] = "NoOp";

				solutions[inNeed.getID()].add(0, curState);
			}	
			
			sol = createSolution(getStrategy("bfs", newInitialState), client, newInitialState);
			
			plan = "Helping agent, by moving away from requests positions, after reverse the latest state";
		}
		
		if(sol == null) {
			System.err.println("REPLAN MUTUAL HELP");
			System.err.println(helper + " and " + inNeed);
			ArrayList<Pos> positions = new ArrayList<>();
			
			for(int i = 0; i < solutions[inNeed.getID()].size(); i++) {
				Node n = solutions[inNeed.getID()].get(i);
				
				if (!positions.contains(n.getRequired())) 
					positions.add(n.getRequired());
			}			

			System.err.println("First part of avoidlist");
			System.err.println("Agent " + helper +" may not be in the following: "+positions);
			
			Pos p = null;
			if(!positions.isEmpty())
				p = positions.get(0);
			
			while(p != null) {
				
				if (p.row - 1 > 0 && !walls[p.row - 1][p.col] && !positions.contains(new Pos(p.row - 1, p.col)))
					p = new Pos(p.row - 1, p.col );
				else if (p.row + 1 < getMaxRow() && !walls[p.row + 1][p.col] && !positions.contains(new Pos(p.row + 1, p.col)))
					p = new Pos(p.row + 1, p.col);
				else if (p.col - 1 > 0 && !walls[p.row][p.col - 1] && !positions.contains(new Pos(p.row, p.col - 1)))
					p = new Pos(p.row, p.col - 1);
				else if (p.col + 1 < getMaxCol() && !walls[p.row][p.col + 1] && !positions.contains(new Pos(p.row, p.col + 1)))
					p = new Pos(p.row, p.col + 1);
				
				if (!positions.contains(p) && oneway(p))
					positions.add(p);
				else
					break;
			}
			
			inNeed.isHelping = helper;
			inNeed.getHelp = false;
			helper.getHelp = true;

			System.err.println("Complete avoidlist");
			System.err.println("Agent " + helper + " may not be in the following: " + positions);

			System.err.println("Helper: " + helper);
			System.err.println("Helper.getsHelp: " + helper.getsHelp);
			System.err.println("Helper.isHelping: " + helper.isHelping);
			System.err.println("InNeed: " + inNeed);
			System.err.println("InNeed.getsHelp: " + inNeed.getsHelp);
			System.err.println("InNeed.isHelping: " + inNeed.isHelping);

			newInitialState.ignore = true;
			inNeed.getHelp = false;
			helper.getHelp = true;
			newInitialState.requestedPositions = positions;
			sol = createSolution(getStrategy("astar", newInitialState), client, newInitialState);
			
			plan = "Helping agent, by moving away from avoidlist";
		}
		
		System.err.println("Agent " + helper + " found a new plan to help his friend");

		System.err.println("New initial state:");
		System.err.println(newInitialState);
		
		System.err.println("New helping plan from :" + plan);
		System.err.println(sol);
		
		solutions[helper.getID()] = sol;
		
		currentStates[helper.getID()] = newInitialState;
		updateRequirements(solutions[helper.getID()], helper.getID());
	}

	private static Node copyNode(Node n, Agent inNeed, Agent helper) {
		Node newInitialState = n.copy();
		newInitialState.help = inNeed;
		if (newInitialState.goToBox != null)
			System.err.println(
					"Helper was fixing box " + newInitialState.goToBox + " at pos " + newInitialState.goToBox.pos);
		if (newInitialState.goToBox != null)
			newInitialState.goToBox.inWorkingProcess = false;
		if (newInitialState.goToBox != null)
			newInitialState.goToBox.goal = null;
		if (newInitialState.goToGoal != null)
			newInitialState.goToGoal.inWorkingProcess = false;
		newInitialState.goToBox = null;
		newInitialState.goToGoal = null;
		newInitialState.ignore = true;
		newInitialState.updateBoxes();

		return newInitialState;
	}

	private static void resetAgent(int i) throws IOException {
		System.err.println("--------- RESET SOLUTIONS ----------- ");
		System.err.println("Agent " + i + " resets its solution");

		Agent a = agentIDs[i];

		a.getHelp = true;
		a.getsHelp = null;
		a.isHelping = null;
		a.waiting = 0;

		if (currentStates[i].goToBox != null || currentStates[i].goToGoal != null) {
			boolean resetBox = true;
			boolean resetGoal = true;
			for (Agent b : agentIDs) {
				if (a != b && currentStates[a.getID()].goToBox == currentStates[b.getID()].goToBox) {
					resetBox = false;
				}
				if (a != b && currentStates[a.getID()].goToGoal == currentStates[b.getID()].goToGoal) {
					resetGoal = false;
				}
			}

			if (resetBox && currentStates[i].goToBox != null)
				currentStates[i].goToBox.inWorkingProcess = false;
			if (resetBox && currentStates[i].goToBox != null)
				currentStates[i].goToBox.goal = null;
			if (resetGoal && currentStates[i].goToGoal != null)
				currentStates[i].goToGoal.inWorkingProcess = false;

			currentStates[i].goToBox = null;
			currentStates[i].goToBox = null;
		}

		solutions[a.getID()] = null;
		actions[a.getID()] = null;

		String c = a.getColor();

		Pos agentPos = null;

		for (int row = 0; row < MAX_ROW; row++) {
			for (int col = 0; col < MAX_COL; col++) {
				if (state.agents[row][col] == a) {
					agentPos = new Pos(row, col);
				}
			}

		}

		System.err.println("Agent " + i + "'s position " + agentPos);
		Node initialState = new Node(null, client, a, agentPos, null);

		initialState.goals = currentStates[i].goals;
		initialState.updateBoxes();

		ArrayList<Box> aBoxes = a.getBoxesNotInGoal();
		Box box = aBoxes.isEmpty() ? null : aBoxes.get(0);

		System.err.println("After reset agent " + i + " box found is: " + box);
		initialState.goToBox = box;
		initialState.goTo = true;

		if (box != null) {
			System.err.println("New goToBox pos: " + box.pos);

			// Set box to be in working process - not to be moved by another agent
			// initialState.goToBox.inWorkingProcess = true;

			Strategy strategy = getStrategy("astar", initialState);
			strategies[a.getID()] = strategy;

			ArrayList<Node> solution = null;
			if (initialState.goToBox != null)
				solution = createSolution(strategy, client, initialState);

			if (solution != null) {
				solutions[a.getID()] = solution;
				currentStates[a.getID()] = initialState;
				updateRequirements(solution, a.getID());
				System.err.println(solution);
			} else {
				System.err.println("COULD NOT FIND SOLUTION FOR " + a);
			}
		} else {
			System.err.println("Agent " + i + " has reached his final goal.");
		}
	}

	private static void orderAgents() {		
		agentOrder = new ArrayList<>();

		Agent a = null;
		for (int i = 0; i < agentCounter; i++) {
			a = agentIDs[i];
			
			Node node = initialStates.get(a);
			ArrayList<Node> solution = solutions[i];

			int index = 0;

			while (index < agentOrder.size()) {
				Agent agent = agentOrder.get(index);
				int priority = 0;
				priority = (initialStates.get(agent).goToGoal != null) ? initialStates.get(agent).goToGoal.priority : Integer.MAX_VALUE;
	
				if (node != null && node.goToGoal != null) {
					priority = initialStates.get(agent).goToGoal.priority;
					
					if (node.goToGoal.priority <= priority)
						break;
				}
				index++;
			}

			agentOrder.add(index, a);

			System.err.println("ADDED: " + a + " at " + index + ": " + agentOrder);
		}
	}

	private static String getAction(ArrayList<Node>[] solutions, int a, int i) throws Exception {
		if (solutions[a] != null && !solutions[a].isEmpty()) {
			Node node = solutions[a].get(0);
			Pos p = node.getRequired();

//			System.err.println(agentIDs[a].getLabel());
//			if (agentIDs[a].getLabel() == '0') {
//				Command reverseAct = Command.reverse(node.action.toString());
//
//				System.err.println("Act normal: " + node.action.toString());
//				System.err.println("Act normal: " + reverseAct.toString());
//				TimeUnit.SECONDS.sleep(1);	
//			}
			
			System.err.println(state);
			System.err.println(p);
			System.err.println((!state.isEmpty(p) && state.agents[p.row][p.col] != node.getAgent()));
			System.err.println(combinedSolution.get(combinedSolution.size() - 1).isEmpty(p));
			if ((!state.isEmpty(p) && state.agents[p.row][p.col] != node.getAgent()) // conflict with other agents in
																						// current state
					|| (!combinedSolution.get(combinedSolution.size() - 1).isEmpty(p)
							&& combinedSolution.get(combinedSolution.size() - 1).agents[p.row][p.col] != node.getAgent())) { // conflict with
																										// beginning of
																										// state

				System.err.println("NO OP 1: " + node.action.toString());

				return "NoOp";
			}
			
			//(node.action.actionType == Command.Type.Push || node.action.actionType == Command.Type.Pull) &&
			System.err.println("Agent " + a + " has goToBox: " + currentStates[a].goToBox + ":" + (currentStates[a].goToBox != null ? currentStates[a].goToBox.pos : ""));
			System.err.println("Agent " + a + " has goToGoal: " + currentStates[a].goToGoal + ":" + (currentStates[a].goToGoal != null ? currentStates[a].goToGoal.getPos() : ""));

			if (currentStates[a].goToBox != null) {
				for (int row = 1; row < MAX_ROW - 1; row++) {
					for (int col = 1; col < MAX_COL - 1; col++) {
						if (currentStates[a].boxes[row][col] == currentStates[a].goToBox 
								&& state.boxes[row][col] != currentStates[a].goToBox) {
							// System.err.println(state);
							// System.err.println(currentStates[a]);
							// System.err.println("Agent " + a + " cannot move the box - the box has been
							// moved");
							System.err.println("ROW: " + row + ", COL: " + col + " ???: " + currentStates[a].boxes[row][col]);
							System.err.println("BOX TO MOVE " + currentStates[a].goToBox + ":" + currentStates[a].goToBox.pos);
							currentStates[a] = currentStates[a].copy();
							resetAgent(a);

							// System.err.println("NoOP - Box has been moved by another agent");
							// System.exit(0);

							return "NoOp";
						}
					}
				}

				
//				if (node.action.actionType == Command.Type.Push) {
//					int row = currentStates[a].agentRow;
//					int col = currentStates[a].agentCol;
//					
//					int newAgentRow = p.row + Command.dirToRowChange(node.action.dir1);
//					int newAgentCol = p.row + Command.dirToColChange(node.action.dir1);
//					
//					int newBoxRow = p.col + Command.dirToRowChange(node.action.dir2);
//					int newBoxCol = p.col + Command.dirToColChange(node.action.dir2);
//					
//					Box box = state.boxes[newAgentRow][newAgentCol];
//					
//					if (box != null && box != currentStates[a].goToBox && box.getLabel() == currentStates[a].goToBox.getLabel() && box.getColor() == currentStates[a].goToBox.getColor()) {
//						TimeUnit.SECONDS.sleep(2);
//						Box oldBox = currentStates[a].goToBox;
//						
//						Node newInitialState = currentStates[a].copy();
//						newInitialState.goToBox = box;
//						newInitialState.goToBox.inWorkingProcess = true;						
//						
//						if (createOwnSolution(newInitialState, a)) {
//							oldBox.inWorkingProcess = false;
//						} else {
//							newInitialState.goToBox.inWorkingProcess = false;
//						}
//						
//						return getAction(solutions, a, i);
//					}
//					
//				} else if (node.action.actionType == Command.Type.Pull) {
//					int row = currentStates[a].agentRow;
//					int col = currentStates[a].agentCol;
//					
//					
//					Command reverseAct = Command.reverse(node.action.toString());
//
//					System.err.println("Act normal: " + node.action.toString());
//					System.err.println("Act normal: " + reverseAct.toString());
//					TimeUnit.SECONDS.sleep(2);
//					
//					int newAgentRow = p.row + Command.dirToRowChange(reverseAct.dir1);
//					int newAgentCol = p.row + Command.dirToColChange(reverseAct.dir1);
//					
//					int newBoxRow = p.col + Command.dirToRowChange(node.action.dir2);
//					int newBoxCol = p.col + Command.dirToColChange(node.action.dir2);
//					
//					Box box = state.boxes[newAgentRow][newAgentCol];
//					
//					if (box != null && box != currentStates[a].goToBox && box.getLabel() == currentStates[a].goToBox.getLabel() && box.getColor() == currentStates[a].goToBox.getColor()) {
//						TimeUnit.SECONDS.sleep(2);
//						Box oldBox = currentStates[a].goToBox;
//						
//						Node newInitialState = currentStates[a].copy();
//						newInitialState.goToBox = box;
//						newInitialState.goToBox.inWorkingProcess = true;						
//						
//						if (createOwnSolution(newInitialState, a)) {
//							oldBox.inWorkingProcess = false;
//						} else {
//							newInitialState.goToBox.inWorkingProcess = false;
//						}
//						
//						return getAction(solutions, a, i);
//					}
//				}
			}

			// Can execute
			System.err.println("*************CHANGE STATE*************");
			state = new MultiNode(state, a, node.action);

			// node.action.toString();
			requests[a][0] = currentStates[a].getRequired();
			
			if (a == 1)
				System.err.println("old current state:------------------------------------ \n" + currentStates[a]);
			
			backupStates[a] = currentStates[a];
			currentStates[a] = node;
			
			if (a == 1)
				System.err.println("new current state:------------------------------------ \n" + currentStates[a]);
			
			System.err.println("state: " + state);
			solutions[a].remove(0);
			updateRequirements(solutions[a], a);
			

			if (solutions[a].isEmpty() && node.goToBox == null) {
				agentIDs[a].waiting = 4;
				System.err.println("Done helping another agent");
			}

			return node.action.toString();
		}
		System.err.println(a + ": NO OP 3");
		return "NoOp";
	}
	
	private static void updateRequirements(ArrayList<Node> solution, int a) {
		if(solution != null) {
			for(int i = 1; i < requests[a].length; i++) {
				if (solution.size() > i - 1) {
					requests[a][i] = solution.get(i - 1).getRequired();
				} else {
					requests[a][i] = null;	
				}
			}
		}
			
//		if(solution != null) {
//			requests[a][1] = null;
//			requests[a][2] = null;
//			requests[a][3] = null;
//			requests[a][4] = null;
//			requests[a][5] = null;
//			requests[a][6] = null;
//
//			if (!solution.isEmpty()) {
//				requests[a][1] = solution.get(0).getRequired();
//				if (solution.size() > 1) {
//					requests[a][2] = solution.get(1).getRequired();
//					if (solution.size() > 2) {
//						requests[a][3] = solution.get(2).getRequired();
//						if (solution.size() > 3) {
//							requests[a][4] = solution.get(3).getRequired();
//							if (solution.size() > 4) {
//								requests[a][5] = solution.get(4).getRequired();
//								if (solution.size() > 5) {
//									requests[a][6] = solution.get(5).getRequired();
//								}
//							}
//						}
//					}
//				}
//			}
	}

	private static ArrayList<Node> createSolution(Strategy strategy, AIClient client, Node initialState) throws IOException {
		ArrayList<Node> solution;
		try {
			System.err.println("Create new solution");
			solution = client.search(strategy, initialState);
		} catch (OutOfMemoryError ex) {
			System.err.println("Maximum memory usage exceeded.");
			solution = null;
		}

		return solution;
	}

	public String getColor(char g) {
		return goalColor.get(g);
	}

	public Goal[][] getGoals() {
		return goals;
	}

	public int getAgentNum() {
		return agentCounter;
	}

	public MultiNode getCurrentState() {
		return combinedSolution.get(combinedSolution.size()-1);
	}

	public MultiNode getCurrentSubState() {
		return state;
	}

	public Node getCurrentState(int i) {
		return currentStates[i];
	}

	private Goal getPrioritizedGoal(Box box, int a) {
		Goal goal = null;
		
		int boxRow = -1;
		int boxCol = -1;
		int distanceToGoal = Integer.MAX_VALUE;
		
		for (int row = 1; row < MAX_ROW - 1; row++) {
			for (int col = 1; col < MAX_COL - 1; col++) {
				if (boxes[row][col] == box) {
					boxRow = row;
					boxCol = col;
					break;
				}
			}
		}
		
		if (box != null) {
			System.err.println("CHECK BOXES MM");
			
			System.err.println(state);
			for (int i = 0; i < goalPriorityList.size(); i++) {
				for (int j = 0; j < goalPriorityList.get(i).size(); j++) {
				
					Goal maybeGoal = goalPriorityList.get(i).get(j);
					
					if (agentIDs[a].reachableGoals.contains(maybeGoal) && Character.toLowerCase(box.getLabel()) == maybeGoal.getLabel() && !maybeGoal.inWorkingProcess) {
					
						Box maybeBox = state.boxes[maybeGoal.getPos().row][maybeGoal.getPos().col];
						
						if (maybeBox == null || Character.toLowerCase(maybeBox.getLabel()) != maybeGoal.getLabel()) {
							if (dijkstraGoalMap.get(maybeGoal)[boxRow][boxCol] < distanceToGoal) {
								distanceToGoal = dijkstraGoalMap.get(maybeGoal)[boxRow][boxCol];
								goal = maybeGoal;
							}
						}

					}
				}
				if (goal != null) return goal;
			}
		}
		return null;
	}
}
