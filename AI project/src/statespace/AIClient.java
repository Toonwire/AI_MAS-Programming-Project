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

import sampleclients.MultiCommand;
import statespace.Strategy.*;
import statespace.Command.Dir;
import statespace.Command.Type;
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

	private Map<Character, ArrayList<Goal>> goalListMap = new HashMap<>();
	private Map<Goal, Integer[][]> dijkstraMap = new HashMap<>();
	
	private final static Set<String> COLORS = new HashSet<>(
			Arrays.asList("blue", "red", "green", "cyan", "magenta", "orange", "pink", "yellow"));
	private static final String DEFAULT_COLOR = "blue";

	private static LinkedList<MultiNode> combinedSolution;
	private static Pos[][] requests;
	private static String[] actions;
	private static Node[] currentStates;
	private static MultiNode state;
	private static LinkedList<Agent> agentOrder;
	private static boolean[] agentsInTrouble;
	private static boolean[] agentsHelping;

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

					Box box = new Box(chr,c);
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
					
					if (!goalListMap.containsKey(chr)) goalListMap.put(chr, new ArrayList<Goal>());
					goalListMap.get(chr).add(goal);
					
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

		for (Map.Entry<Character, ArrayList<Goal>> entry : goalListMap.entrySet()) {
			ArrayList<Goal> goals = entry.getValue();
			
			for (Goal goal : goals) {
				
				Integer[][] dijkstra = new Integer[MAX_ROW][MAX_COL];
				
				// Pair of currentPos, corresponding parentPos
				LinkedList<Pair<Pos,Pos>> queue = new LinkedList<>();
				
				queue.add(new Pair<Pos, Pos>(goal.getPos(), null));
				
				while(true) {	
					
					if (queue.isEmpty()) break;
					
					Pair<Pos, Pos> pair = queue.poll();
					Pos pos = (Pos) pair.getLeft();
					Pos parentPos = (Pos) pair.getRight();
					
					if (dijkstra[pos.row][pos.col] != null || walls[pos.row][pos.col]) continue;
					
					if (parentPos == null) {
						dijkstra[pos.row][pos.col] = 0;
					} else {
						dijkstra[pos.row][pos.col] = dijkstra[parentPos.row][parentPos.col] + 1;
					}
					
					if (0 <= pos.row - 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row - 1, pos.col), pos));
					if (MAX_ROW > pos.row + 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row + 1, pos.col), pos));
					if (0 <= pos.col- 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col - 1), pos));
					if (MAX_COL > pos.col + 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col + 1), pos));
				}
				
				dijkstraMap.put(goal, dijkstra);
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
							LinkedList<Node> sol = Search(getStrategy("astar", initialState), initialState);
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
	 
	public Map<Character, ArrayList<Goal>> getGoalListMap() {
		return this.goalListMap;
	}
	
	public Map<Goal, Integer[][]> getDijkstraMap() {
		return this.dijkstraMap;
	}
	
	public LinkedList<Node> Search(Strategy strategy, Node initialState) throws IOException {
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
			
			// Goal states
			if (leafNode.isGoalState()) {
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
		LinkedList<Node>[] currentSolutions = new LinkedList[initialStates.size()];
		LinkedList<Node>[] remainingSolutions = new LinkedList[initialStates.size()];
		LinkedList<Node>[] nextSolutions = new LinkedList[initialStates.size()];
		
		agentIDs = new Agent[initialStates.size()];
		for (Agent a : initialStates.keySet()) {
			System.err.println("Find solution for agent " + a);
			Node initialState = initialStates.get(a);
			LinkedList<Node> solution = createSolution(getStrategy("astar", initialState), client, initialState);
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
		agentsInTrouble = new boolean[agentIDs.length];
		agentsHelping = new boolean[agentIDs.length];
		
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

			for (Agent a : agentIDs) {
				agentOrder.add(a);
			}
			//agentOrder.add(agentIDs[0]);
			//agentOrder.add(agentIDs[1]);
			
			for(Agent a : initialStates.keySet()) {
				currentStates[a.getID()] = initialStates.get(a);
			}
			String act;
			while(true) {
				completed = new LinkedList<>();
				boolean done = true;
				for (int i = 0; i < solutions.length; i++) { //Current agent
					boolean stop = false;
					System.err.println(agentOrder.toString());
					int a1 = agentOrder.get(i).getID();
					Node currentNode = currentStates[a1]; 	// Get the node / state where we are before we move to the new node 
					Node newNode = null; 					// Get the new node / state
					
					System.err.println("Agent " + a1);
					System.err.println("Agent " + a1 + " - sti længde: " + solutions[a1].size());
					System.err.println("Agent " + a1 + " - backup sti længde1: " + (currentSolutions[a1] != null ? currentSolutions[a1].size() : "null"));
					System.err.println("Agent " + a1 + " - backup sti længde2: " + (remainingSolutions[a1] != null ? remainingSolutions[a1].size() : "null"));
					System.err.println("Agent " + a1 + " - Current node: " + currentNode);
					
					// If an agent has solved an issue by himself, fetch the original plan again
					if (solutions[a1].isEmpty() && nextSolutions[a1] != null) {
						
						// If state is the same as next, remove it
						if (currentStates[a1].agentRow == nextSolutions[a1].getFirst().agentRow && currentStates[a1].agentCol == nextSolutions[a1].getFirst().agentCol) {
							nextSolutions[a1].remove(0);
						}
						
						solutions[a1] = new LinkedList<Node>(nextSolutions[a1]);
						System.err.println(solutions[a1].get(0));
						System.err.println(solutions[a1].get(1));
						System.err.println(solutions[a1].get(2));
						
						currentSolutions[a1] = null;
						remainingSolutions[a1] = null;
						nextSolutions[a1] = null;
						
						updateRequirements(solutions[a1], a1);
						
						requests[a1][0] = new Pos(solutions[a1].get(0).agentRow, solutions[a1].get(0).agentCol);
					}
					
					if ((solutions[a1] == null || solutions[a1].isEmpty()) && remainingSolutions[a1] != null) {
						System.err.println("Agent " + a1 + " - Getting old plan");
						solutions[a1] = new LinkedList<Node>(remainingSolutions[a1]);
						System.err.println(solutions[a1].get(0));
						System.err.println(solutions[a1].get(1));
						System.err.println(solutions[a1].get(2));
						remainingSolutions[a1] = null;
						currentSolutions[a1] = null;
						updateRequirements(solutions[a1], a1);
						requests[a1][0] = new Pos(solutions[a1].get(0).agentRow, solutions[a1].get(0).agentCol); 
					}
					
					if(solutions[a1] != null && !solutions[a1].isEmpty()) {
						newNode = solutions[a1].get(0);
					}
					
					System.err.println("Agent " + a1 + " - TEST: " + requests[a1][0] + " " + new Pos(currentNode.agentRow, currentNode.agentCol) + " " + requests[a1][1] + " " + requests[a1][2] +  " " + requests[a1][3]);
					//Check other agents
					for(int j = 0; j < i; j++) { //Higher-order agents
						
						int a2 = agentOrder.get(j).getID();
						Pos pos = requests[a2][1];
						Pos pos2 = requests[a2][2];
						
						if (!agentsInTrouble[j]) {
							if (pos != null && newNode != null && newNode.getRequired().equals(pos)) {
								System.err.println("Agent " + a1 + " -  wants to enter "+pos+ " in \n"+currentNode);
								actions[a1] = "NoOp";
								stop = true;
							}
							
							continue;
						}
						
						//Fulfill request for next iteration (overwrites above)
						if((pos != null && !currentNode.isEmpty(pos)) || (pos2 != null && !currentNode.isEmpty(pos2))) {
							//Replan
							LinkedList<Pos> positions = new LinkedList<>();
							positions.add(requests[a2][0]); //required in previous state
							positions.add(new Pos(currentStates[a2].agentRow, currentStates[a2].agentCol)); //agent position
							positions.add(pos); //required in current state
							positions.add(pos2); //required in next state		
							positions.add(requests[a2][3]); //required after next state
							Node newInitialState = currentNode.copy();
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
							System.err.println("Agent " + a1 + " - WUUUUUT!!");
							
							if (!agentsHelping[a1]) {
								// add fictive walls and goals where the conflict arose
								for (int row = 0; row < client.getMaxRow(); row++) {
									for (int col = 0; col < client.getMaxCol(); col++) {
										
										// Add wall if another color box is at this position
										if (state.boxes[row][col] != null && state.boxes[row][col].color != currentNode.getAgent().color) {
											client.getTempWalls()[row][col] = true;
										}
										
										// Add wall, if another agent is at this position
										if (state.agents[row][col] != null && !state.agents[row][col].equals(currentNode.getAgent())) {
											client.getTempWalls()[row][col] = true;
										}
									}
								}
								
								newInitialState.requestedPositions = positions;
								solutions[a1] = createSolution(getStrategy("astar", newInitialState), client, newInitialState);
								
								client.resetTempWalls();
								
								agentsHelping[a1] = true;
								
								updateRequirements(solutions[a1], a1);
								System.err.println(solutions[a1]);
							}

							if ((solutions[a1] == null || solutions[a1].isEmpty()) && agentsHelping[a1]) {
								agentsHelping[a1] = false;
							}
							
							stop = false;
							break;

						} 
					}
					
					if(!stop) {
						actions[a1] = getAction(solutions, currentSolutions, remainingSolutions, nextSolutions, a1, i);
					}
					
					//Create solution to solve own problem
					if((solutions[a1] == null || solutions[a1].isEmpty()) && !currentNode.isGoalState()) {
						System.err.println("Agent " + a1 + " - Finding new way");
						Node newInitialState = newNode.copy();
						solutions[a1] = createSolution(getStrategy("astar", newInitialState), client, newInitialState);
						updateRequirements(solutions[a1], a1);
					}
					
					//Completed goals
					if(currentNode.isGoalState()) {
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
//				for(Agent a : completed) {
//					agentOrder.remove(a);
//					agentOrder.add(a);
//				}
				
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
	
	private static String getAction(LinkedList<Node>[] solutions, LinkedList<Node>[] currentSolutions,  LinkedList<Node>[] remainingSolutions, LinkedList<Node>[] nextSolutions,int a, int i) throws Exception {
		if (solutions[a] != null && !solutions[a].isEmpty()) {
			Node node = solutions[a].get(0);
			
			Pos p = node.getRequired();
			
			// If trying to replan, the robot will fail for 5 times, if one of the next moves are going to be inside another box, i.e. conflicting
			if ((!state.isEmpty(p) && state.agents[p.row][p.col] != node.getAgent()) // conflict with other agents in same state
					|| !(combinedSolution.get(combinedSolution.size() - 1)).isEmpty(p)) { // conflict with previous
																							// state
				
				if (!agentsInTrouble[a]) {
					System.err.println("Agent " + a + " - finder en ny vej");
					
					// Check if there is another way to solve this conflict before requesting help
					int lookAhead = 2;
					if (node.action.actionType == Type.Move) {
						lookAhead = 1;
					}
					
					Node nextNode = null;
					if (remainingSolutions[a] != null && remainingSolutions[a].size() >= 1) {
						nextNode = remainingSolutions[a].poll();
						//System.err.println("Agent " + a + " - Original sti længde: " + currentSolutions[a].size());
						System.err.println("Agent " + a + " - SemiOriginal sti længde: " + remainingSolutions[a].size());
					} else if (solutions[a].size() >= lookAhead) {
						nextNode = solutions[a].get(lookAhead);
						System.err.println("Agent " + a + " - første sti længde: " + solutions[a].size());
						System.err.println("Agent " + a + " - FAKE Original sti længde: " + (currentSolutions[a] != null ? currentSolutions[a].size() : "null"));
						System.err.println("Agent " + a + " - FAKE SemiOriginal sti længde: " + (remainingSolutions[a] != null ? remainingSolutions[a].size() : "null"));
					} else {
						System.err.println("Agent " + a + " - Ikke langt igen!!!");
						for (Node n : solutions[a]) {
							System.err.println(n);
						}
					}
					
					boolean findNewPath = remainingSolutions[a] != null && currentSolutions[a] != null ? remainingSolutions[a].size() > currentSolutions[a].size() - 5 : true;
					
					if (findNewPath && nextNode != null) {
						Node newInitialNode = currentStates[a].copy();
						newInitialNode.goals = new Goal[client.getMaxRow()][client.getMaxCol()];
						newInitialNode.agentGoal = new Pos(nextNode.agentRow, nextNode.agentCol);
						
						System.err.println("Agent " + a + " - Vi finder ny plan");
						System.err.println("Agent " + a + " - action: " + node.action.toString() + ", " + "(" + node.agentRow + "," + node.agentCol + ") - " + requests[a][0] + requests[a][1] + requests[a][2] + requests[a][3]);

						// add fictive walls and goals where the conflict arose
						for (int row = 0; row < client.getMaxRow(); row++) {
							for (int col = 0; col < client.getMaxCol(); col++) {
								
								// Add new goals
								if (nextNode.boxes[row][col] != null) {
									newInitialNode.goals[row][col] = new Goal(Character.toLowerCase(nextNode.boxes[row][col].getLabel()), new Pos(row,col));
								}
								boolean isAgentGoal = newInitialNode.agentGoal.equals(new Pos(row,col));
								
								// Ad wall if another color box is at this position and there is no goal for the current agent
								if (state.boxes[row][col] != null && state.boxes[row][col].color != node.getAgent().color && newInitialNode.goals[row][col] == null && !isAgentGoal) {
									client.getTempWalls()[row][col] = true;
								}
								
								// Add wall, if another agent is at this position and there is no goal for the current agent
								if (state.agents[row][col] != null && !state.agents[row][col].equals(node.getAgent()) && newInitialNode.goals[row][col] == null && !isAgentGoal) {
									client.getTempWalls()[row][col] = true;
								}
							}
						}
						
						System.err.println(newInitialNode);
//						try {
//							TimeUnit.SECONDS.sleep(2);
//						} catch (InterruptedException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
						
						//client.getTempWalls()[node.getRequired().row][node.getRequired().col] = true;
						LinkedList<Node> tempSolution = createSolution(getStrategy("astar", newInitialNode), client, newInitialNode);
						
						client.resetTempWalls();
						
						if (tempSolution != null && !tempSolution.isEmpty()) {
							System.err.println("Agent " + a + " - Vi fandt ny plan");
							System.err.println("Agent " + a + " - Ny plan er " + tempSolution.size() + " lang");
							System.err.println(tempSolution.toString());
							
							if (currentSolutions[a] == null) {
								
								currentSolutions[a] = new LinkedList<Node>(solutions[a]);
								
								if (remainingSolutions[a] == null) {
									while(lookAhead > 0) {
										solutions[a].remove(0);
										lookAhead--;
									}
									remainingSolutions[a] = new LinkedList<Node>(solutions[a]);
								}
							}
							
							solutions[a] = tempSolution;
							updateRequirements(solutions[a], a);
							requests[a][0] = new Pos(solutions[a].get(0).agentRow, solutions[a].get(0).agentCol);
							
							return getAction(solutions, currentSolutions, remainingSolutions, nextSolutions, a, i);
						} else {
							System.err.println("Agent " + a + " - TO BAD!!!!!! NO SOLUTION FOUND");
							
							// Not sure if this should be here as well
							System.err.println("Agent " + a + " - Getting old plan4");
							
							if (currentSolutions[a] != null)
								solutions[a] = new LinkedList<Node>(currentSolutions[a]);
							
							currentSolutions[a] = null;
							
							System.err.println("Agent " + a + " - Spørg om hjælp2!");
							agentsInTrouble[a] = true;
							// No other plan was found
							
							System.err.println("Agent " + a + " - NOOP GOOOOOOO 4");
						}
					} else if (!agentsInTrouble[a]) {
						System.err.println("Agent " + a + " - Getting old plan3");
						
						if (currentSolutions[a] != null)
							solutions[a] = new LinkedList<Node>(currentSolutions[a]);
						
						currentSolutions[a] = null;
						remainingSolutions[a] = new LinkedList<Node>(nextSolutions[a]);
						
						agentsInTrouble[a] = true;
						System.err.println("Agent " + a + " - Spørg om hjælp!");
						// No other plan was found
						
						System.err.println("Agent " + a + " - NOOP GOOOOOOO 2");
					}
				} else {
					System.err.println("Agent " + a + " - Waiting for help");
				}
				System.err.println("Agent " + a + " - NOOP GOOOOOOO 5");
				System.err.println("Agent " + a + " - Spørg om hjælp3!");
			} else {
				
				System.err.println("Agent " + a + " - Not NoOp GOOOOOOO 1");
				
//				if (agentsInTrouble[a]) {
//					System.err.println("Steps left: \n" + solutions[a].size());
//					System.err.println("Current State: \n" + currentStates[a]);
//					System.err.println("Next State: \n" + node);
//					System.err.println("Next next State: \n" + solutions[a].get(1));
//					
//				}
				
				agentsInTrouble[a] = false;
				
				state = new MultiNode(state, a, node.action);
				System.err.println(node.action.toString());
				node.action.toString();
				currentStates[a] = node;
				solutions[a].remove(0);
				
				currentSolutions[a] = null;
				
				if (remainingSolutions[a] != null) nextSolutions[a] = new LinkedList<Node>(remainingSolutions[a]);
				
				if (solutions[a].isEmpty() && nextSolutions[a] != null) {
					System.err.println("Agent " + a + " - Last step " + node);
					System.err.println("Agent " + a + " - Getting old plan2");
					
					// If state is the same as next, remove it
					if (currentStates[a].agentRow == nextSolutions[a].getFirst().agentRow && currentStates[a].agentCol == nextSolutions[a].getFirst().agentCol) {
						nextSolutions[a].remove(0);
					}
					
					solutions[a] = new LinkedList<Node>(nextSolutions[a]);
					System.err.println(solutions[a].get(0));
					System.err.println(solutions[a].get(1));
					System.err.println(solutions[a].get(2));
					
					currentSolutions[a] = null;
					remainingSolutions[a] = null;
					nextSolutions[a] = null;
					
					updateRequirements(solutions[a], a);
					
					requests[a][0] = new Pos(solutions[a].get(0).agentRow, solutions[a].get(0).agentCol);
				} else {
					updateRequirements(solutions[a], a);	
				}
				
				
				return node.action.toString();
			}
		}
		System.err.println("Agent " + a + " - NOOP GOOOOOOO 3");
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

	private static LinkedList<Node> createSolution(Strategy strategy, AIClient client, Node initialState)
			throws IOException {
		LinkedList<Node> solution;
		try {
			solution = client.Search(strategy, initialState);
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
