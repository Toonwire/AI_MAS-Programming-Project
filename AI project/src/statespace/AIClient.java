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

import sampleclients.MultiCommand;
import statespace.Strategy.*;
import statespace.Heuristic.*;

public class AIClient {
	private static int MAX_ROW;
	private static int MAX_COL;

	private boolean[][] walls;
	private boolean[][] tempWalls;
	private static Goal[][] goals;
	private static Agent[][] agents;
	private static Agent[] agentIDs;
	private static Box[][] boxes;

	private List<Goal> goalList;
	private Map<String, Box[][]> boxMap = new HashMap<>();
	private Map<String, Goal[][]> goalMap = new HashMap<>();
	private Map<Goal, String> goalColor = new HashMap<>();
	// private Map<Character, ArrayList<Goal>> goalMap;
	private static Map<Agent, Node> initialStates = new HashMap<>();

	private final static Set<String> COLORS = new HashSet<>(
			Arrays.asList("blue", "red", "green", "cyan", "magenta", "orange", "pink", "yellow"));
	private static final String DEFAULT_COLOR = "blue";

	private static LinkedList<MultiNode> combinedSolution;
	private static Pos[][] requests;
	private static String[] actions;
	private static Node[] currentStates;
	private static MultiNode state;
	private static LinkedList<Agent> agentOrder;

	public class Agent {
		private char id;
		private String color;

		public Agent(char id, String color) {
			this.id = id;
			this.color = color;
			System.err.println("Found " + color + " agent " + id);
		}

		public String act() {
			return new MultiCommand(MultiCommand.type.Pull, MultiCommand.dir.W, MultiCommand.dir.N).toActionString();
		}

		public String getColor() {
			return color;
		}

		public char getLabel() {
			return id;
		}

		public int getID() {
			return Character.getNumericValue(id);
		}

		@Override
		public String toString() {
			return "" + id;
		}
	}

	// private BufferedReader in = new BufferedReader(new
	// InputStreamReader(System.in));
	// private List<Agent> agents = new ArrayList<Agent>();

	private AIClient(BufferedReader in) throws IOException {
		Map<Character, String> chrColorMap = new HashMap<>();
		Map<String, List<Character>> colorChrMap = new HashMap<>();
		Map<String, Integer> colorAgents = new HashMap<>();
		Map<String, LinkedList<Goal>> colorGoals = new HashMap<>();
		
		String line, color;

		// Read lines specifying colors
		while ((line = in.readLine()).matches("^[a-z]+:\\s*[0-9A-Z](,\\s*[0-9A-Z])*\\s*$")) {
			line = line.replaceAll("\\s", "");
			color = line.split(":")[0];
			if (!COLORS.contains(color))
				throw new ColorException("Color not defined");
			else if (colorChrMap.containsKey(color))
				throw new ColorException("Color defined multiple times..");

			List<Character> colorObjects = new ArrayList<>();
			for (String id : line.split(":")[1].split(",")) {
				colorObjects.add(id.charAt(0));
				chrColorMap.put(id.charAt(0), color);
			}
			colorChrMap.put(color, colorObjects);
			colorAgents.put(color, 0);
			colorGoals.put(color, new LinkedList<Goal>());
		}

		// Max columns and rows
		MAX_COL = line.length();
		LinkedList<String> lines = new LinkedList<>();
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

					Agent a = new Agent(chr, c);
					agents[row][col] = a;
					colorAgents.put(c, colorAgents.get(c)+1);
				} else if ('A' <= chr && chr <= 'Z') { // Box.
					String c = chrColorMap.get(chr);
					if (c == null)
						c = DEFAULT_COLOR;

					Box box = new Box(chr);
					boxes[row][col] = box;
					boxMap.get(c)[row][col] = box;
				} else if ('a' <= chr && chr <= 'z') { // Goal.
					String c = chrColorMap.get(Character.toUpperCase(chr));
					if (c == null)
						c = DEFAULT_COLOR;

					Goal goal = new Goal(chr, new Pos(row, col));
					goals[row][col] = goal;
					goalMap.get(c)[row][col] = goal;
					goalColor.put(goal, c);

					goalList.add(goal);

					colorGoals.get(c).add(goal);
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

		//Create initial states
		for (int row = 0; row < MAX_ROW; row++) {
			for (int col = 0; col < MAX_COL; col++) {
				Agent a = agents[row][col];
				if (a != null) {
					String c = a.getColor();
					Node initialState = new Node(null, this, a);
					initialState.agentCol = col;
					initialState.agentRow = row;
					initialState.boxes = boxMap.get(c);
					
					//If agents of same color, only solve for reachable goals 
					if(colorAgents.get(c) > 1) {
						Goal[][] reachableGoals = new Goal[MAX_ROW][MAX_COL];
						for(Goal g : colorGoals.get(c)) {
							Goal[][] gsTemp = new Goal[MAX_ROW][MAX_COL]; //array with single goal to test
							int row2 = g.getPos().row;
							int col2 = g.getPos().col;
							gsTemp[row2][col2] = g;
							initialState.goals = gsTemp; 
							LinkedList<Node> sol = Search(getStrategy("astar", initialState), initialState, null, null);
							if(sol != null && !sol.isEmpty()) {
								reachableGoals[row2][col2] = g; //add to final goal array
							}
						}
						
						initialState.goals = reachableGoals;
					} else {
						initialState.goals = goalMap.get(c);
					}
					initialStates.put(a, initialState);
				}
			}
		}
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
	            // You're welcome to test WA* out with different values, but for the report you must at least indicate benchmarks for W = 5.
	            strategy = new StrategyBestFirst(new WeightedAStar(initialState, 5));
	            break;
	        case "greedy":
	            strategy = new StrategyBestFirst(new Greedy(initialState));
	            break;
	        default:
	            strategy = new StrategyBFS();
	            System.err.println("Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy.");
		
		}
		return strategy;
	}

	public int getMaxRow() {
		return MAX_ROW;
	}

	public int getMaxCol() {
		return MAX_COL;
	}

	public Goal[][] getGoals(Agent agent) {
		return goalMap.get(agent.getColor());
	}

	public List<Goal> getGoalList() {
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

	public LinkedList<Node> Search(Strategy strategy, Node initialState, LinkedList<Pos> pos, Node goalNode) throws IOException {
		System.err.format("Search starting with strategy %s.\n", strategy.toString());
		strategy.addToFrontier(initialState);
		int iterations = 0;
		while (true) {
			if (iterations == 1000) {
				System.err.println(strategy.searchStatus());
				iterations = 0;
			}

			if (strategy.frontierIsEmpty()) {
				return null;
			}

			Node leafNode = strategy.getAndRemoveLeaf();

			if ((pos == null && leafNode.isGoalState()) 
					|| (pos != null && leafNode.requestFulfilled(pos) && leafNode.parent != null && leafNode.parent.isEmpty(pos.get(1)))
					|| (goalNode != null && leafNode.isSubGoalState(goalNode))) {
				return leafNode.extractPlan();
			}

			strategy.addToExplored(leafNode);
			for (Node n : leafNode.getExpandedNodes()) {
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
//					n.calculateDistanceToGoal();
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}

	public static AIClient client;
	
	public static void main(String[] args) throws Exception {
		BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in));

		// Read level and create the initial state of the problem
		client = new AIClient(serverMessages);
		
//		  Strategy strategy = null; if (args.length > 0) { switch
//		  (args[0].toLowerCase()) { case "-bfs": strategy = new StrategyBFS(); break;
//		  case "-dfs": strategy = new StrategyDFS(); break; case "-astar": // strategy
//		  = new StrategyBestFirst(new AStar(client.initialState)); break; case
//		  "-wastar": // You're welcome to test WA* out with different values, but for
//		  the report you // must at least indicate benchmarks for W = 5. // strategy =
//		  new StrategyBestFirst(new WeightedAStar(client.initialState, 5)); break; case
//		  "-greedy": // strategy = new StrategyBestFirst(new
//		  Greedy(client.initialState)); break; default: strategy = new StrategyBFS();
//		  System.err.println(
//		  "Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy."
//		  ); } } else { strategy = new StrategyBFS(); System.err.println(
//		  "Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy."
//		  ); }
		 

		LinkedList<Node>[] solutions = new LinkedList[initialStates.size()];
		LinkedList<Node>[] originalSolutions = new LinkedList[initialStates.size()];

		agentIDs = new Agent[initialStates.size()];
		for (Agent a : initialStates.keySet()) {
			Node initialState = initialStates.get(a);
			LinkedList<Node> solution = createSolution(getStrategy("astar", initialState), client, initialState, null);
			agentIDs[a.getID()] = a;
					
			if (solution != null) {
				solutions[a.getID()] = solution;
			} else {
				System.err.println("COULD NOT FIND SOLUTION");
			}
		}
		combinedSolution = new LinkedList<>();
		requests = new Pos[initialStates.size()][4];
		actions = new String[initialStates.size()];
		currentStates = new Node[initialStates.size()];
		agentOrder = new LinkedList<>();
		LinkedList<Agent> completed = new LinkedList<>();
		if (false) {// temp
			// System.err.println(strategy.searchStatus());
			// System.err.println("Unable to solve level.");
			// System.exit(0);
		} else {
			// System.err.println("\nSummary for " + strategy.toString());
			// System.err.println("Found solution of length " + solution.size());
			// System.err.println(strategy.searchStatus());
			
			
//			Initial State
			state = new MultiNode(client, boxes, agents);
			combinedSolution.add(state);

			agentOrder.add(agentIDs[0]);
			agentOrder.add(agentIDs[1]);
			
			for(Agent a : initialStates.keySet()) {
				currentStates[a.getID()] = initialStates.get(a);
			}
			String act;
			while(true) {
				completed = new LinkedList<>();
				boolean done = true;
				for (int i = 0; i < solutions.length; i++) { //Current agentf
					boolean stop = false;
					int a1 = agentOrder.get(i).getID();
					Node beforeNode = currentStates[a1];
					Node afterNode = null;
					if(solutions[a1] != null && !solutions[a1].isEmpty()) {
						afterNode = solutions[a1].get(0);
					}
					
					//Check other agents
					for(int j = 0; j < i; j++) { //Higher-order agents
						int a2 = agentOrder.get(j).getID();
						Pos pos = requests[a2][1];
						Pos pos2 = requests[a2][2];
						//Fulfil request for next iteration (overwrites above)
						if((pos != null && !beforeNode.isEmpty(pos)) || (pos2 != null && !beforeNode.isEmpty(pos2))) {
							//Replan
							LinkedList<Pos> positions = new LinkedList<>();
							positions.add(requests[a2][0]); //required in previous state
							positions.add(new Pos(currentStates[a2].agentRow, currentStates[a2].agentCol)); //agent position
							positions.add(pos); //required in current state
							positions.add(pos2); //required in next state		
							positions.add(requests[a2][3]); //required after next state
							
							Node newInitialState = beforeNode.copy();
//							new Thread(new Runnable() {
//								@Override
//								public void run() {
//									try {
//										createSolution(getStrategy(newInitialState), client, newInitialState, positions);
//									} catch (IOException e) {
//										// TODO Auto-generated catch block
//										e.printStackTrace();
//									}
//								}
//							}).start();
							System.err.println("WUUUUUT!!");
							solutions[a1] = createSolution(getStrategy("bfs", newInitialState), client, newInitialState, positions);
							updateRequirements(solutions[a1], a1);
							stop = false;
							break;
						} else if (pos != null && afterNode != null && afterNode.getRequired().equals(pos)) {
							System.err.println(a1+ " wants to enter "+pos+ " in \n"+beforeNode);
							actions[a1] = "NoOp";
							stop = true;
						}
					}

					if ((solutions[a1] == null || solutions[a1].isEmpty()) && originalSolutions[a1] != null) {
						solutions[a1] = originalSolutions[a1];
						originalSolutions[a1] = null;
					}
					
					if(!stop) {
						actions[a1] = getAction(solutions, originalSolutions, a1, i);
					}
					
					//Create solution to solve own problem
					if((solutions[a1] == null || solutions[a1].isEmpty()) && !currentStates[a1].isGoalState()) {
						Node newInitialState = currentStates[a1].copy();
						solutions[a1] = createSolution(getStrategy("astar", newInitialState), client, newInitialState, null);
						updateRequirements(solutions[a1], a1);
					}
					
					//Completed goals
					if(currentStates[a1].isGoalState()) {
						completed.add(agentIDs[a1]);
					}
					
					//At least one agent has a proper action
					if(actions[a1] != "NoOp") {
						done = false;
					}
				}
				
				if(done) {
					break;
				}
				
				//Create action string
				act = "[";
				for(int i = 0; i<actions.length; i++) {
					act += actions[i];
					if(i < actions.length-1) {
						act += ", ";
					}
				}
				act += "]";
				
				//Reorder
				for(Agent a : completed) {
					agentOrder.remove(a);
					agentOrder.add(a);
				}
				
				combinedSolution.add(state);
				System.err.println(act);
				System.out.println(act);
				System.err.println(combinedSolution.get(combinedSolution.size()-1));
				 String response = serverMessages.readLine();
				 if (response.contains("false")) {
					 System.err.format("Server responsed with %s to the inapplicable action: %s\n", response, act);
					 System.err.format("%s was attempted in \n%s\n", act, state.toString());
					 break;
				 }
			}
		}
	}
	
	private static String getAction(LinkedList<Node>[] solutions, LinkedList<Node>[] originalSolutions, int a, int i) throws Exception {
		if (solutions[a] != null && !solutions[a].isEmpty()) {
			Node node = solutions[a].get(0);
			
			Pos p = node.getRequired();

			if ((!state.isEmpty(p) && state.agents[p.row][p.col] != node.getAgent()) // conflict with other agents in same state
					|| !(combinedSolution.get(combinedSolution.size() - 1)).isEmpty(p)) { // conflict with previous
																							// state
				
				// Check if there is another way to solve this conflict before requesting help
				Node nextNode = null;
				if (originalSolutions[a] != null && originalSolutions[a].size() > 1)
					nextNode = originalSolutions[a].get(1);
				else if (solutions[a].size() > 1)
					nextNode = solutions[a].get(1);
				
				if (nextNode != null) {
					Node newInitialNode = currentStates[a].copy();
					System.err.println("Vi finder ny plan");
					// add fictive wall where the conflict arose
					client.getTempWalls()[node.getRequired().row][node.getRequired().col] = true;
					LinkedList<Node> tempSolution = createSolution(getStrategy("bfs", newInitialNode), client, newInitialNode, null, nextNode);
					
					if (!tempSolution.isEmpty()) {
						System.err.println("Vi fandt ny plan");
						System.err.println(tempSolution.toString());
						originalSolutions[a] = solutions[a];
						solutions[a] = tempSolution;
						
						return getAction(solutions, originalSolutions, a, i);
					}
				} else {
					System.err.println("Spørg om hjælp!");
					// No other plan was found
					client.resetTempWalls();
					return "NoOp";					
				}
			}
			
			client.resetTempWalls();
			
			state = new MultiNode(state, a, node.action);
			
			node.action.toString();
			currentStates[a] = node;
			solutions[a].remove(0);
			updateRequirements(solutions[a], a);
			return node.action.toString();
		}
		return "NoOp";
	}
	
	private static void updateRequirements(LinkedList<Node> solution, int a) {
		if(solution != null) {
			requests[a][0] = requests[a][1];
			requests[a][1] = null;
			requests[a][2] = null;
			requests[a][3] = null;
			if (!solution.isEmpty()) {
				requests[a][1] = solution.get(0).getRequired();
				if (solution.size() > 1) {
					requests[a][2] = solution.get(1).getRequired();
					if (solution.size() > 2) {
						requests[a][3] = solution.get(2).getRequired();
					}
				}
			}
		}
	}

	private static LinkedList<Node> createSolution(Strategy strategy, AIClient client, Node initialState, LinkedList<Pos> pos)
			throws IOException {
		return createSolution(strategy, client, initialState, pos, null);
	}
	
	private static LinkedList<Node> createSolution(Strategy strategy, AIClient client, Node initialState, LinkedList<Pos> pos, Node goalNode)
			throws IOException {
		LinkedList<Node> solution;
		try {
			solution = client.Search(strategy, initialState, pos, goalNode);
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
		return initialStates.size();
	}

}
