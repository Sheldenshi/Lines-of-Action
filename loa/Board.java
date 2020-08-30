/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import java.util.regex.Pattern;

import static loa.Piece.*;
import static loa.Square.*;

/** Represents the state of a game of Lines of Action.
 *  @author Shelden Shi
 */
class Board {

    /** Default number of moves for each side that results in a draw. */
    static final int DEFAULT_MOVE_LIMIT = 60;

    /** Pattern describing a valid square designator (cr). */
    static final Pattern ROW_COL = Pattern.compile("^[a-h][1-8]$");

    /** A Board whose initial contents are taken from INITIALCONTENTS
     *  and in which the player playing TURN is to move. The resulting
     *  Board has
     *        get(col, row) == INITIALCONTENTS[row][col]
     *  Assumes that PLAYER is not null and INITIALCONTENTS is 8x8.
     *
     *  CAUTION: The natural written notation for arrays initializers puts
     *  the BOTTOM row of INITIALCONTENTS at the top.
     */
    Board(Piece[][] initialContents, Piece turn) {
        initialize(initialContents, turn);
    }

    /** A new board in the standard initial position. */
    Board() {
        this(INITIAL_PIECES, BP);
    }

    /** A Board whose initial contents and state are copied from
     *  BOARD. */
    Board(Board board) {
        this();
        copyFrom(board);
    }

    /** Set my state to CONTENTS with SIDE to move. */
    void initialize(Piece[][] contents, Piece side) {
        int index = 0;
        for (int x = 0; x < BOARD_SIZE; x++) {
            for (int y = 0; y < BOARD_SIZE; y++) {
                _board[index] = contents[x][y];
                index++;
            }
        }
        _turn = side;
        _moves.clear();
        _moveLimit = DEFAULT_MOVE_LIMIT;
    }

    /** Set me to the initial configuration. */
    void clear() {
        initialize(INITIAL_PIECES, BP);
        _moves.clear();
        _winnerKnown = false;
        _winner = null;
        _subsetsInitialized = false;
    }

    /** Set my state to a copy of BOARD. */
    void copyFrom(Board board) {
        for (int i = 0; i < _board.length; i++) {
            _board[i] = board._board[i];
        }
        _moves.addAll(board._moves);
        _turn = board.turn();
        _moveLimit = board._moveLimit;
        _winnerKnown = board._winnerKnown;
        _winner = board._winner;
        _subsetsInitialized = false;
    }

    /** Return the contents of the square at SQ. */
    Piece get(Square sq) {
        return _board[sq.index()];
    }

    /** Set the square at SQ to V and set the side that is to move next
     *  to NEXT, if NEXT is not null. */
    void set(Square sq, Piece v, Piece next) {
        _board[sq.index()] = v;
        _turn = next;
    }

    /** Set the square at SQ to V, without modifying the side that
     *  moves next. */
    void set(Square sq, Piece v) {
        set(sq, v, null);
    }

    /** Set limit on number of moves by each side that results in a tie to
     *  LIMIT, where 2 * LIMIT > movesMade(). */
    void setMoveLimit(int limit) {
        if (2 * limit <= movesMade()) {
            throw new IllegalArgumentException("move limit too small");
        }
        _moveLimit = 2 * limit;
    }

    /** Assuming isLegal(MOVE), make MOVE. Assumes MOVE.isCapture()
     *  is false. */
    void makeMove(Move move) {
        assert isLegal(move);
        Square from = move.getFrom();
        Square to = move.getTo();
        if (get(from) == get(to).opposite()) {
            _moves.add(move.captureMove());
        } else {
            _moves.add(move);
        }
        _board[to.index()] = get(from);
        _board[from.index()] = EMP;
        _turn = turn().opposite();
        centerMassFinderWP();
        centerMassFinderBP();
        _subsetsInitialized = false;
    }

    /** Retract (unmake) one move, returning to the state immediately before
     *  that move.  Requires that movesMade () > 0. */
    void retract() {
        assert movesMade() > 0;
        Move last = _moves.get(movesMade() - 1);
        Square from = last.getFrom();
        Square to = last.getTo();

        if (last.isCapture()) {
            _board[from.index()] = get(to);
            _board[to.index()] = get(to).opposite();
        } else {
            _board[from.index()] = get(to);
            _board[to.index()] = EMP;
        }
        _moves.remove(last);
        _turn = turn().opposite();
        _subsetsInitialized = false;
    }

    /** returns the last two moves of the board for Undo. */
    Move[] getLastTwoMoves() {
        Move[] result = new Move[2];
        result[1] = _moves.get(_moves.size() - 1);
        result[0] = _moves.get(_moves.size() - 2);
        return result;
    }

    /** Return the Piece representing who is next to move. */
    Piece turn() {
        return _turn;
    }
    /** Return true iff FROM - TO is a legal move for the player currently on
     *  move.*/
    boolean isLegal(Square from, Square to) {
        if (from != null && to != null) {
            int dis = from.distance(to);
            int dir = from.direction(to);
            int[] pieceNumAllDirs = numMovesFinder(from);
            int index = indexFinder(dir);
            if (dis != pieceNumAllDirs[index]) {
                return false;
            } else if (blocked(from, to)) {
                return false;
            }
            return true;
        }
        return false;
    }

    /** Return index 0-3.
     * @param dir dir*/
    int indexFinder(int dir) {
        if (dir == 0 || dir == 4) {
            return 0;
        } else if (dir == 1 || dir == 5) {
            return 1;
        } else if (dir == 2 || dir == 6) {
            return 2;
        } else {
            return 3;
        }
    }

    /** Return true iff MOVE is legal for the player currently on move.
     *  The isCapture() property is ignored. */
    boolean isLegal(Move move) {
        if (move != null) {
            return isLegal(move.getFrom(), move.getTo());
        }
        return false;
    }

    /** Return if a piece is being blocked. 0 index is WP, 1 index is BP */
    int[] isBlocked() {
        int[] result = new int[4];
        int countWP = 0;
        int countBP = 0;
        int moveOptionsWP = 0;
        int moveOptionsBP = 0;
        for (int x = 0; x < BOARD_SIZE; x++) {
            for (int y = 0; y < BOARD_SIZE; y++) {
                if (get(Square.sq(x, y)) != EMP) {
                    List<Move> moves = legalMoves(Square.sq(x, y));
                    if (get(sq(x, y)) == WP) {
                        if (moves.isEmpty()) {
                            countWP += 1;
                        } else {
                            moveOptionsWP += moves.size();
                        }
                    } else if (get(sq(x, y)) == BP) {
                        if (moves.isEmpty()) {
                            countBP += 1;
                        } else {
                            moveOptionsBP += moves.size();
                        }
                    }

                }
            }
        }
        result[0] = countWP;
        result[1] = countBP;
        result[2] = moveOptionsWP;
        result[3] = moveOptionsBP;
        return result;
    }

    /** Return a sequence of all legal moves from this position.*/
    List<Move> legalMoves() {
        ArrayList<Move> result = new ArrayList<>();
        for (int x = 0; x < BOARD_SIZE; x++) {
            for (int y = 0; y < BOARD_SIZE; y++) {
                if (get(Square.sq(x, y)) == turn()) {
                    List<Move> moves = legalMoves(Square.sq(x, y));
                    result.addAll(moves);
                }
            }
        }
        return result;
    }
    /** Return a sequence of all legal moves from this position.
     * @param from from*/
    List<Move> legalMoves(Square from) {
        int[] pieceNumAllDirs = numMovesFinder(from);
        ArrayList<Move> result = new ArrayList<>();
        for (int dir = 0; dir < 8; dir++) {
            int index = indexFinder(dir);
            Square to = from.moveDest(dir, pieceNumAllDirs[index]);
            if (isLegal(from, to)) {
                if (get(from) == get(to).opposite()) {
                    result.add(Move.mv(from, to, true));
                } else {
                    result.add(Move.mv(from, to));
                }
            }
        }
        return result;
    }

    /** Return a int[]. Finds the number of pieces in each direction
     * from the position.
     * @param  from from*/
    int[] numMovesFinder(Square from) {
        int[] pieceNumAllDirs = new int[]{1, 1, 1, 1};
        for (int dir = 0; dir < 8; dir++) {
            Square curr = from.moveDest(dir, 1);
            while (curr != null) {
                if (get(curr) != EMP) {
                    if (dir == 0 || dir == 4) {
                        pieceNumAllDirs[0] += 1;
                    } else if (dir == 1 || dir == 5) {
                        pieceNumAllDirs[1] += 1;
                    } else if (dir == 2 || dir == 6) {
                        pieceNumAllDirs[2] += 1;
                    } else if (dir == 3 || dir == 7) {
                        pieceNumAllDirs[3] += 1;
                    }
                }
                curr = curr.moveDest(dir, 1);
            }
        }
        return pieceNumAllDirs;
    }

    /** Return true iff the game is over (either player has all his
     *  pieces continguous or there is a tie). */
    boolean gameOver() {
        return winner() != null;
    }

    /** Return true iff SIDE's pieces are continguous. */
    boolean piecesContiguous(Piece side) {
        return getRegionSizes(side).size() == 1;
    }

    /** Return the winning side, if any.  If the game is not over, result is
     *  null.  If the game has ended in a tie, returns EMP. */
    Piece winner() {
        if (piecesContiguous(WP) && piecesContiguous(BP)) {
            return turn().opposite();
        } else if (piecesContiguous(WP)) {
            return WP;
        } else if (piecesContiguous(BP)) {
            return BP;
        } else if (movesMade() >= _moveLimit) {
            return EMP;
        }
        return _winner;
    }

    /** Return the total number of moves that have been made (and not
     *  retracted).  Each valid call to makeMove with a normal move increases
     *  this number by 1. */
    int movesMade() {
        return _moves.size();
    }

    @Override
    public boolean equals(Object obj) {
        Board b = (Board) obj;
        return Arrays.deepEquals(_board, b._board) && _turn == b._turn;
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(_board) * 2 + _turn.hashCode();
    }

    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("===%n");
        for (int r = BOARD_SIZE - 1; r >= 0; r -= 1) {
            out.format("    ");
            for (int c = 0; c < BOARD_SIZE; c += 1) {
                out.format("%s ", get(sq(c, r)).abbrev());
            }
            out.format("%n");
        }
        out.format("Next move: %s%n===", turn().fullName());
        return out.toString();
    }

    /** Return true if a move from FROM to TO is blocked by an opposing
     *  piece or by a friendly piece on the target square. */
    boolean blocked(Square from, Square to) {
        int dis = from.distance(to);
        int dir = from.direction(to);
        for (int x = 1; x <= dis; x++) {
            Square next = from.moveDest(dir, x);
            if (x == dis) {
                if (get(next) == get(from)) {
                    return true;
                }
            } else {
                if (get(next) == get(from).opposite()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Return the size of the as-yet unvisited cluster of squares
     *  containing P at and adjacent to SQ.  VISITED indicates squares that
     *  have already been processed or are in different clusters.  Update
     *  VISITED to reflect squares counted. */
    private int numContig(Square sq, boolean[][] visited, Piece p) {
        int count = 1;
        Square[] adjacent = sq.adjacent();
        for (Square adj : adjacent) {
            if (!visited[adj.col()][adj.row()]
                    && get(adj) == p) {
                visited[adj.col()][adj.row()] = true;
                count += numContig(adj, visited, p);
            }
        }

        return count;
    }

    /** Set the values of _whiteRegionSizes and _blackRegionSizes. */
    private void computeRegions() {
        if (_subsetsInitialized) {
            return;
        }
        _whiteRegionSizes.clear();
        _blackRegionSizes.clear();

        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int x = 0; x < BOARD_SIZE; x++) {
            for (int y = 0; y < BOARD_SIZE; y++) {
                Square curr = Square.sq(x, y);
                if (get(curr) != EMP
                        && !visited[x][y]) {
                    visited[x][y] = true;
                    int num = numContig(curr, visited, get(curr));
                    if (num != 0) {
                        if (get(curr) == WP) {
                            _whiteRegionSizes.add(num);
                        } else {
                            _blackRegionSizes.add(num);
                        }
                    }
                }
                visited[x][y] = true;
            }
        }
        Collections.sort(_whiteRegionSizes, Collections.reverseOrder());
        Collections.sort(_blackRegionSizes, Collections.reverseOrder());
        _subsetsInitialized = true;
    }

    /** Returns the center of mass for WP. */
    Square getCentermassWP() {
        return centermassWP;
    }

    /** Returns the center of mass for WP. */
    Square getCentermassBP() {
        return centermassBP;
    }

    /** Returns the avg dis from CM for WP. */
    double getAvgDisWP() {
        return avgDisWP;
    }

    /** Returns the avg dis from CM for BP. */
    double getAvgDisBP() {
        return avgDisBP;
    }

    /** Returns the number of Q3 for WP. */
    int getNumQ3WP() {
        return numQ3WP;
    }
    /** Returns the number of Q3 for BP. */
    int getNumQ3BP() {
        return numQ3BP;
    }

    /** Returns the number of pieces at corner for WP. */
    int getAtCornerWP() {
        return atCornerWP;
    }

    /** Returns the number of pieces at corner for BP. */
    int getAtCornerBP() {
        return atCornerBP;
    }

    /** Finds center of mass. */
    void centerMassFinderWP() {
        ArrayList<Square> allWP = new ArrayList<>();
        int xWP; int yWP; int disWP;
        xWP = yWP = disWP = 0;
        for (int x = 0; x < BOARD_SIZE; x++) {
            for (int y = 0; y < BOARD_SIZE; y++) {
                if (get(sq(x, y)) == WP) {
                    xWP += sq(x, y).col();
                    yWP += sq(x, y).row();
                    allWP.add(sq(x, y));
                }
            }
        }
        centermassWP = sq(xWP / allWP.size(), yWP / allWP.size());
        atCornerWP = 0; numQ3WP = 0; int samerow = 8;
        ArrayList<Integer> indexsWP = new ArrayList<>();
        ArrayList<Integer[]> pairsWP = new ArrayList<>();
        for (Square s : allWP) {
            for (int corner : cornerIndex) {
                if (s.index() == corner) {
                    atCornerWP += 1;
                }
            }
            for (int x : indexsWP) {
                if (Math.abs(x - s.index()) == samerow) {
                    pairsWP.add(new Integer[]{Math.min(x, s.index()),
                            Math.max(x, s.index())});
                }
            }
            for (Integer[] pair : pairsWP) {
                if (Math.abs(s.index() - pair[0]) == 1
                        || Math.abs(s.index() - pair[1]) == 1) {
                    numQ3WP += 1;
                }
            }
            indexsWP.add(s.index());
            disWP += Math.abs(s.distance(centermassWP));

        }
        avgDisWP = disWP / allWP.size();
    }
    /** Finds center of mass. */
    void centerMassFinderBP() {
        ArrayList<Square> allBP = new ArrayList<>();
        int xBP; int yBP; int disBP;
        xBP = yBP = disBP = 0;
        for (int x = 0; x < BOARD_SIZE; x++) {
            for (int y = 0; y < BOARD_SIZE; y++) {
                if (get(sq(x, y)) == BP) {
                    allBP.add(sq(x, y));
                    xBP += sq(x, y).col();
                    yBP += sq(x, y).row();
                }
            }
        }
        centermassBP = sq(xBP / allBP.size(), yBP / allBP.size());
        atCornerBP = 0; numQ3BP = 0; int sameRow = 8;
        ArrayList<Integer> indexsBP = new ArrayList<>();
        ArrayList<Integer[]> pairsBp = new ArrayList<>();
        for (Square s : allBP) {
            for (int corner : cornerIndex) {
                if (s.index() == corner) {
                    atCornerBP += 1;
                }
            }
            for (int x : indexsBP) {
                if (Math.abs(x - s.index()) == sameRow) {
                    pairsBp.add(new Integer[]{Math.min(x, s.index()),
                            Math.max(x, s.index())});
                }
            }
            for (Integer[] pair : pairsBp) {
                if (Math.abs(s.index() - pair[0]) == 1
                        || Math.abs(s.index() - pair[1]) == 1) {
                    numQ3BP += 1;
                }
            }
            indexsBP.add(s.index());
            disBP += s.distance(centermassBP);
        }
        avgDisBP = disBP / allBP.size();
    }

    /** Return the sizes of all the regions in the current union-find
     *  structure for side S. */
    List<Integer> getRegionSizes(Piece s) {
        computeRegions();
        if (s == WP) {
            return _whiteRegionSizes;
        } else {
            return _blackRegionSizes;
        }
    }


    /** The standard initial configuration for Lines of Action (bottom row
     *  first). */

    static final Piece[][] INITIAL_PIECES = {
            { EMP, BP,  BP,  BP,  BP,  BP,  BP,  EMP },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { EMP, BP,  BP,  BP,  BP,  BP,  BP,  EMP }
    };

    /** Current contents of the board.  Square S is at _board[S.index()]. */
    private final Piece[] _board = new Piece[BOARD_SIZE  * BOARD_SIZE];



    /** Seven. */
    private final int seven = 7;

    /** Number of pieces that are blocked. */
    private int[] cornerIndex = new int[] {0, seven, seven * 8, seven * 9};
    /** Center of mass for BP. */
    private Square centermassBP;

    /** Center of mass for WP. */
    private Square centermassWP;

    /** Avg dis to CM for WP. */
    private double avgDisWP;

    /** Avg dis to CM for BP. */
    private double avgDisBP;

    /** number of piece at a corner for WP. */
    private int atCornerWP;

    /** number of piece at a corner for BP. */
    private int atCornerBP;

    /** number of Q3 or Q4s. */
    private int numQ3WP;

    /** number of Q3 or Q4s. */
    private int numQ3BP;

    /** List of all unretracted moves on this board, in order. */
    private final ArrayList<Move> _moves = new ArrayList<>();
    /** Current side on move. */
    private Piece _turn;
    /** Limit on number of moves before tie is declared.  */
    private int _moveLimit;
    /** True iff the value of _winner is known to be valid. */
    private boolean _winnerKnown;
    /** Cached value of the winner (BP, WP, EMP (for tie), or null (game still
     *  in progress).  Use only if _winnerKnown. */
    private Piece _winner;

    /** True iff subsets computation is up-to-date. */
    private boolean _subsetsInitialized;

    /** List of the sizes of continguous clusters of pieces, by color. */
    private final ArrayList<Integer>
            _whiteRegionSizes = new ArrayList<>(),
            _blackRegionSizes = new ArrayList<>();

}
