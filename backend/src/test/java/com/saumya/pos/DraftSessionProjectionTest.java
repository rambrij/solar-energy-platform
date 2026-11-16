package com.saumya.pos;
import com.saumya.pos.retail.DraftSessionProjection;
import com.saumya.pos.shared.PosDraftStateChanged;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DraftSessionProjectionTest {
 @Test void draftAndResolutionFactsAlwaysUseTheCompleteRetailScope(){var commands=new java.util.ArrayList<String>();var db=new JdbcTemplate(){@Override public int update(String sql,Object...args){commands.add(sql);assertThat(args).containsExactly(7L,9L,"cashier","shift","order");return 1;}};var projection=new DraftSessionProjection(db);projection.changed(new PosDraftStateChanged(7,9,"cashier","shift","order","DRAFT"));for(String state:java.util.List.of("PAID","CANCELLED","EXPIRED"))projection.changed(new PosDraftStateChanged(7,9,"cashier","shift","order",state));assertThat(commands.getFirst()).startsWith("INSERT INTO pos_active_drafts");assertThat(commands.subList(1,4)).allMatch(sql->sql.startsWith("DELETE FROM pos_active_drafts WHERE tenant_id=? AND branch_id=? AND cashier=? AND session_id=? AND order_reference=?"));}
 @Test void incompleteOrUnknownFactsCannotReachPersistence(){assertThatThrownBy(()->new PosDraftStateChanged(1,1,"cashier",null,"order","DRAFT")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new PosDraftStateChanged(1,1,"cashier","shift","order","UNKNOWN")).isInstanceOf(IllegalArgumentException.class);}
}
