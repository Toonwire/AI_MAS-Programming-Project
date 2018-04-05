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

public class AIClient {
	private static int MAX_ROW;
	private static int MAX_COL;

	private boolean[][] walls;
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
	private static int[] agentOrder;
	
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
			return ""+id;
		}
	}

	// private BufferedReader in = new BufferedReader(new
	// InputStreamReader(System.in));
	// private List<Agent> agents = new ArrayList<Agent>();

	private AIClient(BufferedReader in) throws IOException {
		Map<Character, String> chrColorMap = new HashMap<>();
		Map<String, List<Character>> colorChrMap = new HashMap<>();
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

					Goal goal = new Goal(chr, row, col);
					goals[row][col] = goal;
					goalMap.get(c)[row][col] = goal;
					goalColor.put(goal, c);

					goalList.add(goal);
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

		for (int row = 0; row < MAX_ROW; row++) {
			for (int col = 0; col < MAX_COL; col++) {
				Agent a = agents[row][col];
				if (a != null) {
					String c = a.getColor();
					Node initialState = new Node(null, this, a);
					initialState.agentCol = col;
					initialState.agentRow = row;
					initialState.boxes = boxMap.get(c);
					initialState.goals = goalMap.get(c);
					// initialState.goals = goals;
					// agents.add(a);

					initialStates.put(a, initialState);
				}
			}
		}
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

	public Box[][] getBoxes() {
		return boxes;
	}

	public Agent[][] getAgents() {
		return agents;
	}

	public LinkedList<Node> Search(Strategy strategy, Node initialState, LinkedList<Pos> pos) throws IOException {
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
				|| (pos != null && leafNode.requestFulfilled(pos) && leafNode.parent != null 
				&&  leafNode.parent.isEmpty(pos.getFirst()))) {
				return leafNode.extractPlan();
			}

			strategy.addToExplored(leafNode);
			for (Node n : leafNode.getExpandedNodes()) {
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					n.calculateDistanceToGoal();
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}

	public static void main(String[] args) throws Exception {
		BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in));

		// Read level and create the initial state of the problem
		AIClient client = new AIClient(serverMessages);
		/*
		 * Strategy strategy = null; if (args.length > 0) { switch
		 * (args[0].toLowerCase()) { case "-bfs": strategy = new StrategyBFS(); break;
		 * case "-dfs": strategy = new StrategyDFS(); break; case "-astar": // strategy
		 * = new StrategyBestFirst(new AStar(client.initialState)); break; case
		 * "-wastar": // You're welcome to test WA* out with different values, but for
		 * the report you // must at least indicate benchmarks for W = 5. // strategy =
		 * new StrategyBestFirst(new WeightedAStar(client.initialState, 5)); break; case
		 * "-greedy": // strategy = new StrategyBestFirst(new
		 * Greedy(client.initialState)); break; default: strategy = new StrategyBFS();
		 * System.err.println(
		 * "Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy."
		 * ); } } else { strategy = new StrategyBFS(); System.err.println(
		 * "Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy."
		 * ); }
		 */

		LinkedList<Node>[] solutions = new LinkedList[initialStates.size()];

		agentIDs = new Agent[initialStates.size()];
		for (Agent a : initialStates.keySet()) {
			Node initialState = initialStates.get(a);
			LinkedList<Node> solution = createSolution(client, initialState, null);
			agentIDs[a.getID()] = a;
					
			if (solution != null) {
				solutions[a.getID()] = solution;
			} else {
				System.err.println("COULD NOT FIND SOLUTION");
			}
		}
		combinedSolution = new LinkedList<>();
		requests = new Pos[initialStates.size()][3];
		actions = new String[initialStates.size()];
		currentStates = new Node[initialStates.size()];
		agentOrder = new int[initialStates.size()];

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


			agentOrder[0] = 0;
			agentOrder[1] = 1;
			for(Agent a : initialStates.keySet()) {
				currentStates[a.getID()] = initialStates.get(a);
			}
			String act;
			while(true) {
				boolean done = true;
				for (int i = 0; i < solutions.length; i++) { //Current agent
					boolean stop = false;
					int a1 = agentOrder[i];
					Node beforeNode = currentStates[a1];
					Node afterNode = null;
					if(solutions[a1] != null && !solutions[a1].isEmpty()) {
						afterNode = solutions[a1].get(0);
					}
					
					//Check other agents
					for(int j = 0; j < i; j++) { //Higher-order agents
						int a2 = agentOrder[j];
						Pos pos = requests[a2][0];
						Pos pos2 = requests[a2][1];
										
						//Fulfil request for next iteration (overwrites above)
						if((pos != null && !beforeNode.isEmpty(pos)) || (pos2 != null && !beforeNode.isEmpty(pos2))) {
							//Replan
							LinkedList<Pos> positions = new LinkedList<>();
							positions.add(new Pos(currentStates[a2].agentRow, currentStates[a2].agentCol));
							positions.add(pos);
							positions.add(pos2);		
							positions.add(requests[a2][2]);
							solutions[a1] = createSolution(client, beforeNode.copy(), positions);
							stop = false;
							break;
						} else if (pos != null && afterNode != null && afterNode.getRequired().equals(pos)) {
							actions[a1] = "NoOp";
							stop = true;
						}
					}
					
					if(!stop) {
						actions[a1] = getAction(solutions, a1, i);
					}
					
					//Create solution to solve own problem
					if((solutions[a1] == null || solutions[a1].isEmpty()) && !currentStates[a1].isGoalState()) {
						solutions[a1] = createSolution(client, currentStates[a1].copy(), null);
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
	
	private static String getAction(LinkedList<Node>[] solutions, int a, int i) {
		if (solutions[a] != null && !solutions[a].isEmpty()) {
			Node node = solutions[a].get(0);
			
			Pos p = node.getRequired();

			if((!state.isEmpty(p) && state.agents[p.row][p.col] != node.getAgent()) //conflict with other agents in same state
					|| !(combinedSolution.get(combinedSolution.size()-1)).isEmpty(p)) { //conflict with previous state
				return "NoOp";
			} else {
				state = new MultiNode(state, a, node.action);
			}
			
			node.action.toString();
			currentStates[a] = node;
			solutions[a].remove(0);
			//Add request
			requests[a][0] = null;
			requests[a][1] = null;
			requests[a][2] = null;
			if(!solutions[a].isEmpty()) {
				requests[a][0] = solutions[a].get(0).getRequired();
				if(solutions[a].size() > 1) {
					requests[a][1] = solutions[a].get(1).getRequired();
					if(solutions[a].size() > 2) {
						requests[a][2] = solutions[a].get(2).getRequired();
					}
				}
			}
			return node.action.toString();
		} 
		return "NoOp";
	}

	private static LinkedList<Node> createSolution(AIClient client, Node initialState, LinkedList<Pos> pos) throws IOException {
		LinkedList<Node> solution;
		try {
			solution = client.Search(new StrategyBFS(), initialState, pos);
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
