package paulevs.edenring.world.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.betterx.bclib.noise.OpenSimplexNoise;
import org.betterx.bclib.util.BlocksHelper;
import org.betterx.bclib.util.MHelper;
import paulevs.edenring.noise.InterpolationCell;
import paulevs.edenring.noise.VoronoiNoise;

import java.util.Random;

public class CaveGenerator {
	private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
	private static final VoronoiNoise VORONOI_NOISE = new VoronoiNoise();
	private static OpenSimplexNoise simplexNoise;
	private static int seed;
	
	public static void init(long seed) {
		Random random = new Random(seed);
		simplexNoise = new OpenSimplexNoise(random.nextInt());
		CaveGenerator.seed = random.nextInt();
	}
	
	public static void carve(ChunkAccess chunkAccess, InterpolationCell cellTerrain) {
		short minX = (short) chunkAccess.getPos().getMinBlockX();
		short minZ = (short) chunkAccess.getPos().getMinBlockZ();
		short minY = (short) chunkAccess.getMinBuildHeight();
		short maxY = (short) chunkAccess.getMaxBuildHeight();
		
		final float[] buffer9 = new float[9];
		final float[] buffer27 = new float[27];
		
		Random random = new Random();
		final int maxCell = (maxY - minY) / 8 + 1;
		final BlockPos origin = new BlockPos(minX, minY, minZ);
		TerrainGenerator generator = MultiThreadGenerator.getTerrainGenerator();
		InterpolationCell cellSparse = new InterpolationCell(generator, 4, (maxY - minY) / 16 + 1, 16, 16, new BlockPos(minX - 16, minY, minZ - 16));
		InterpolationCell cellVoronoi = new InterpolationCell(p -> getTunelNoise(p, buffer27, random), 5, (maxY - minY) / 4 + 1, 4, 4, origin);
		InterpolationCell cellBigCave = new InterpolationCell(p -> getBigCaveNoise(cellSparse, p), 3, maxCell, 8, 8, origin);
		InterpolationCell cellPillars = new InterpolationCell(p -> getPillars(p, seed, buffer9, random), 5, (maxY - minY) / 4 + 1, 4, 4, origin);
		
		MutableBlockPos pos = new MutableBlockPos();
		
		short newMinY = (short) MHelper.min(cellTerrain.getMinY(), cellSparse.getMinY());
		minY = (short) MHelper.max(minY, newMinY);
		int maxCheck = maxY - 16;
		
		for (int x = 0; x < 16; x++) {
			pos.setX(x);
			for (int z = 0; z < 16; z++) {
				pos.setZ(z);
				int index = 0;
				float[] accumulation = new float[8];
				short max = (short) chunkAccess.getHeight(Types.WORLD_SURFACE_WG, x, z);
				for (short y = max; y >= minY; y--) {
					if (y < maxCheck) {
						float heightNoise = cellTerrain.get(pos.setY(y + 10), true);
						if (heightNoise <= 0) {
							continue;
						}
						if (y > 8) {
							heightNoise = cellTerrain.get(pos.setY(y - 8), true);
							if (heightNoise <= 0) {
								continue;
							}
						}
					}
					
					LevelChunkSection section = chunkAccess.getSection(y >> 4);
					pos.setY(y);
					
					if (y < maxCheck) {
						float heightNoise = cellTerrain.get(pos.setY(y + 10), true);
						if (heightNoise <= 0) {
							continue;
						}
						if (y > 8) {
							heightNoise = cellTerrain.get(pos.setY(y - 8), true);
							if (heightNoise <= 0) {
								continue;
							}
						}
					}
					
					if (section.getBlockState(x, y & 15, z).is(Blocks.STONE)) {
						float noise = cellVoronoi.get(pos, true);
						accumulation[index] = noise;
						index = (index + 1) & 7;
						
						float average = 0;
						for (int i = 0; i < accumulation.length; i++) {
							noise = MHelper.max(noise, accumulation[i]);
							average += accumulation[i];
						}
						noise = (noise + (average / accumulation.length)) * 0.5F - 0.9F;
						
						float cellValue = cellTerrain.get(pos, true);
						noise = -smoothUnion(-noise, cellValue + 0.5F, 1.1F);
						
						float bigCave = 0;
						if (noise < 0.1F) {
							bigCave = cellBigCave.get(pos, true);
							noise = -smoothUnion(-noise, -bigCave, 0.1F);
						}
						
						if (noise > -0.1F) {
							float pillars = cellPillars.get(pos, true);
							noise = smoothUnion(noise, pillars, 0.1F);
						}
						
						if (noise > 0) {
							section.setBlockState(x, y & 15, z, CAVE_AIR, false);
							int py = pos.getY();
							pos.setY(py + 1);
						}
					}
				}
			}
		}
	}
	
	private static float getTunelNoise(BlockPos pos, float[] buffer, Random random) {
		VORONOI_NOISE.getDistances(seed, pos.getX() * 0.01, pos.getY() * 0.03, pos.getZ() * 0.01, buffer, random);
		return buffer[0] / buffer[2];
	}
	
	private static float smoothUnion(float a, float b, float radius) {
		float h = Mth.clamp(0.5F + 0.5F * (b - a) / radius, 0F, 1F);
		return Mth.lerp(h, b, a) - radius * h * (1F - h);
	}
	
	private static float getMinValue(InterpolationCell cell, BlockPos pos) {
		float value = 1;
		for (Direction dir : BlocksHelper.DIRECTIONS) {
			float side = cell.get(pos.relative(dir, 15), false);
			value = MHelper.min(value, side);
		}
		return value;
	}
	
	private static float getBigCaveNoise(InterpolationCell cell, BlockPos pos) {
		if (pos.getY() < 32 || pos.getY() > 224) {
			return 0;
		}
		
		float noise = (float) simplexNoise.eval(pos.getX() * 0.03, pos.getY() * 0.03, pos.getZ() * 0.03);
		
		float value = getMinValue(cell, pos);
		value = MHelper.max(value, getMinValue(cell, pos.above(8)));
		value = MHelper.max(value, getMinValue(cell, pos.below(8)));
		if (noise < 0) {
			value += noise;
		}
		
		noise = (float) simplexNoise.eval(pos.getX() * 0.1, pos.getY() * 0.1, pos.getZ() * 0.1) * 0.004F + 0.004F;
		noise += (float) simplexNoise.eval(pos.getX() * 0.03, pos.getY() * 0.03, pos.getZ() * 0.03) * 0.01F + 0.01F;
		
		return value - noise;
	}
	
	private static float getPillars(BlockPos pos, int seed, float[] buffer, Random random) {
		VORONOI_NOISE.getDistances(seed, pos.getX() * 0.02, pos.getZ() * 0.02, buffer, random);
		float value = VORONOI_NOISE.getValue(seed, pos.getX() * 0.02, pos.getZ() * 0.02, random);
		value = buffer[0] - 0.07F * (value * 0.5F + 0.5F);
		return value + (float) simplexNoise.eval(pos.getX() * 0.03, pos.getY() * 0.03, pos.getZ() * 0.03) * 0.01F;
	}
}
