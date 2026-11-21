import {cleanup,render,screen} from '@testing-library/react';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {ProtectedRoute} from './ProtectedRoute';
import {usePermissions} from './usePermissions';

vi.mock('./usePermissions',()=>({usePermissions:vi.fn()}));
afterEach(()=>{cleanup();vi.clearAllMocks();});

describe('ProtectedRoute',()=>{
  it('denies an empty permission policy instead of treating it as public',()=>{
    vi.mocked(usePermissions).mockReturnValue({
      permissions:{username:'staff@example.test',tenantId:1,branchId:1,permissions:['POS_VIEW'],actorType:'VENDOR'},
      loading:false,error:null,hasPermission:()=>true,hasAnyPermission:()=>true,hasAllPermissions:()=>true,
      isPlatformUser:false,isCustomer:false,isVendorEmployee:true
    });
    render(<ProtectedRoute requiredPermissions={[]}>Protected content</ProtectedRoute>);
    expect(screen.queryByText('Protected content')).toBeNull();
    expect(screen.getByRole('heading',{name:'Access Denied'})).toBeTruthy();
  });
});
