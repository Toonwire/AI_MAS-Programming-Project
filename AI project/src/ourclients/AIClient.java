package ourclients;

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
import statespace.Box;
import statespace.ColorException;
import statespace.Goal;
import statespace.Node;

public class AIClient {
	private int MAX_ROW;
	private int MAX_COL;
	private boolean[][] walls;
	private Goal[][] goals; // Not in use for now!
	private List<Goal> goalList;
	private Map<String,Box[][]> boxMap = new HashMap<>();
	private Map<String,Goal[][]> goalMap = new HashMap<>();
	//private Map<Character, ArrayList<Goal>> goalMap;
	private Map<Agent, Node> initialStates = new HashMap<>();
	
	private final static Set<String> COLORS = new HashSet<>(Arrays.asList("blue", "red", "green", "cyan", "magenta", "orange", "pink", "yellow"));
	
	public class Agent {
		public Agent(char id, String color) {
			System.err.println("Found " + color + " agent " + id);
		}
		
		public String act() {
			return new MultiCommand(MultiCommand.type.Pull, MultiCommand.dir.W, MultiCommand.dir.N).toActionString();
		}
	}

	private BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	private List<Agent> agents = new ArrayList<Agent>();

	private void readMap() throws IOException {
 		Map<Character, String> chrColorMap = new HashMap<Character, String>();
		Map<String, List<Character>> colorChrMap = new HashMap<>();
		String line, color;

		// Read lines specifying colors
		while ((line = in.readLine()).matches("^[a-z]+:\\s*[0-9A-Z](,\\s*[0-9A-Z])*\\s*$")) {
			line = line.replaceAll("\\s", "");
			color = line.split(":")[0];			
			if (!COLORS.contains(color)) throw new ColorException("Color not defined");
			else if (colorChrMap.containsKey(color)) throw new ColorException("Color defined multiple times..");
			
			List<Character> colorObjects = new ArrayList<>();
			for (String id : line.split(":")[1].split(",")) {
				colorObjects.add(id.charAt(0));
				chrColorMap.put(id.charAt(0), color);
			}
			
			colorChrMap.put(color, colorObjects);
		} 

		//Max columns and rows
		MAX_COL = line.length();	
		LinkedList<String> lines = new LinkedList<>();
		while(!line.equals("")){
			lines.add(line);
			line = in.readLine();
			MAX_COL = line.length() > MAX_COL ? line.length() : MAX_COL;
		}	
		MAX_ROW = lines.size();
		
		//Initialize arrays
		
		for (String currentColor : colorChrMap.keySet()) {
			boxMap.put(currentColor,  new Box[MAX_ROW][MAX_COL]);
			goalMap.put(currentColor,  new Goal[MAX_ROW][MAX_COL]);
		}
		
		walls = new boolean[MAX_ROW][MAX_COL];
		goals = new Goal[MAX_ROW][MAX_COL];
		goalList = new ArrayList<Goal>();
		//goalMap = new HashMap<Character, ArrayList<Goal>>();
		// Read lines specifying level layout
		
		
		for (int row = 0; row < lines.size(); row++) {
			line = lines.get(row);
			
			for (int col = 0; col < line.length(); col++) {
				char chr = line.charAt(col);

				if (chr == '+') { // Wall.
					walls[row][col] = true;
				} else if ('0' <= chr && chr <= '9') { // Agent.
					String c = chrColorMap.get(chr);
					Agent a = new Agent(chr, c);
					Node initialState = new Node(null, this);
					initialState.agentCol = col;
					initialState.agentRow = row;
					initialState.boxes = boxMap.get(c);
					agents.add(a);
					initialStates.put(a, initialState);
				} else if ('A' <= chr && chr <= 'Z') { // Box.
					String c = chrColorMap.get(chr);
					boxMap.get(c)[row][col] = new Box(chr);
				} else if ('a' <= chr && chr <= 'z') { // Goal.
					String c = chrColorMap.get(chr);
					
					Goal goal = new Goal(chr, row, col);
					goalList.add(goal);
					goals[row][col] = goal;
					
					goalMap.get(c)[row][col] = goal;
					
					//if (!goalMap.containsKey(chr))
					//	goalMap.put(chr, new ArrayList<Goal>());
					//goalMap.get(chr).add(goal);
					
				} else if (chr == ' ') {
					// Free space.
				} else {
					System.err.println("Error, read invalid level character: " + chr);
					System.exit(1);
				}
			}
		}
	}
	
	public int getMaxRow() {
		return MAX_ROW;
	}
	
	public int getMaxCol(){
		return MAX_COL;
	}
	
	public Goal[][] getGoals(){
		return goals;
	}
	
	public List<Goal> getGoalList(){
		return goalList;
	}
	
	public Map<String, Goal[][]> getGoalMap(){
		return goalMap;
	}
	
	public boolean[][] getWalls(){
		return walls;
	}

}
