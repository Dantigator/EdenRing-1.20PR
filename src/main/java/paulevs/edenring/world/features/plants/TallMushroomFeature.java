package paulevs.edenring.world.features.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.betterx.bclib.util.BlocksHelper;
import org.betterx.bclib.util.MHelper;
import paulevs.edenring.blocks.EdenBlockProperties;
import paulevs.edenring.registries.EdenBlocks;
import paulevs.edenring.world.features.basic.ScatterFeature;

public class TallMushroomFeature extends ScatterFeature {
	public TallMushroomFeature() {
		super(EdenBlocks.TALL_BALLOON_MUSHROOM);
	}
	
	@Override
	protected int getCount(RandomSource random) {
		return MHelper.randRange(5, 10, random);
	}
	
	@Override
	protected void placeBlock(WorldGenLevel level, BlockPos pos, BlockState state) {
		int height = level.getRandom().nextInt(4);
		MutableBlockPos p = pos.mutable();
		for (int i = 0; i <= height; i++) {
			state = state.setValue(EdenBlockProperties.TEXTURE_4, (int) i);
			if (i == height) state = state.setValue(EdenBlockProperties.TEXTURE_4, 3);
			if (level.getBlockState(p.above()).isAir()) {
				BlocksHelper.setWithoutUpdate(level, p, state);
				p.setY(p.getY() + 1);
			}
			else {
				state = state.setValue(EdenBlockProperties.TEXTURE_4, 3);
				BlocksHelper.setWithoutUpdate(level, p, state);
				return;
			}
		}
	}
}
